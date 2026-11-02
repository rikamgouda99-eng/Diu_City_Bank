package com.dcb.service;

import com.dcb.config.BankProperties;
import com.dcb.entity.CardEntity;
import com.dcb.entity.CardOtpEntity;
import com.dcb.entity.UserEntity;
import com.dcb.exception.BankException;
import com.dcb.repository.CardOtpRepo;
import com.dcb.repository.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OTPs that confirm credit card payments. The OTP is mailed to the card owner, stored only as a
 * BCrypt hash, allows a few wrong attempts and can be used once.
 */
@Service
@Slf4j
public class CardOtpService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int RESEND_AFTER_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CardOtpRepo otpRepo;
    private final UserRepo userRepo;
    private final MailService mailService;
    private final PasswordEncoder encoder;
    private final BankProperties props;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public CardOtpService(CardOtpRepo otpRepo, UserRepo userRepo, MailService mailService, PasswordEncoder encoder, BankProperties props) {
        this.otpRepo = otpRepo;
        this.userRepo = userRepo;
        this.mailService = mailService;
        this.encoder = encoder;
        this.props = props;
    }

    @Transactional
    public void send(CardEntity card) {
        LocalDateTime now = LocalDateTime.now();
        otpRepo.findById(card.getCardId()).ifPresent(existing -> {
            if (existing.getCreatedAt().plusSeconds(RESEND_AFTER_SECONDS).isAfter(now)) {
                throw new BankException("An OTP was just sent. Please wait " + RESEND_AFTER_SECONDS + " seconds before asking for a new one.");
            }
        });
        UserEntity user = userRepo.findById(card.getBankId()).orElseThrow(() -> new BankException("User not found."));
        if (user.getMailId() == null || user.getMailId().isBlank()) {
            throw new BankException("No email address is registered for OTP delivery.");
        }
        String otp = String.valueOf(100000 + RANDOM.nextInt(900000));
        int validity = props.getCard().getOtpValidityMinutes();
        otpRepo.save(CardOtpEntity.builder()
                .cardId(card.getCardId())
                .otpHash(encoder.encode(otp))
                .expiry(now.plusMinutes(validity))
                .attempts(0)
                .createdAt(now)
                .build());
        String email = user.getMailId();
        executor.submit(() -> {
            try {
                mailService.sendCardOtpMail(email, otp, card.getMaskedNumber(), validity);
            } catch (Exception e) {
                log.warn("{} could not send card OTP mail: {}", card.getBankId(), e.getMessage());
            }
        });
        log.info("{} card payment OTP sent for card {}", card.getBankId(), card.getCardId());
    }

    /**
     * Checks and consumes the OTP. Runs in its own transaction that is not rolled back on a wrong OTP,
     * so failed attempts are counted even though the payment itself is rejected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BankException.class)
    public void verify(Long cardId, String otp) {
        if (otp == null || !otp.matches("\\d{6}")) {
            throw new BankException("Please enter the 6 digit OTP sent to your registered email.");
        }
        CardOtpEntity record = otpRepo.findById(cardId)
                .orElseThrow(() -> new BankException("Please request an OTP first."));
        if (record.getExpiry().isBefore(LocalDateTime.now())) {
            otpRepo.delete(record);
            throw new BankException("OTP has expired, please request a new one.");
        }
        if (!encoder.matches(otp, record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            if (record.getAttempts() >= MAX_ATTEMPTS) {
                otpRepo.delete(record);
                throw new BankException("Incorrect OTP entered too many times, please request a new one.");
            }
            otpRepo.save(record);
            throw new BankException("Incorrect OTP. " + (MAX_ATTEMPTS - record.getAttempts()) + " attempt(s) left.");
        }
        otpRepo.delete(record);
    }
}
