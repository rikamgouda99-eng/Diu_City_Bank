package com.dcb.service;


import com.dcb.entity.OtpEntity;
import com.dcb.exception.BankException;
import com.dcb.repository.OtpRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class OtpService {

    @Autowired
    OtpRepo otpRepo;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    MailService mailService;


    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    public void generateOtp(String mailId){
        logger.info("{} request to generate otp",mailId);

        if(mailId == null){
            logger.info("MailID can't be null to generate the otp");
            throw new BankException("Can't generate otp, because MailId is missing");
        }

        Long random = 100000L + new Random().nextLong(900000L);
        String encodedOtp = encoder.encode(String.valueOf(random));
        logger.info("{} otp generated successfully.",mailId);
        OtpEntity otpEntity = new OtpEntity();
        otpEntity.setMailId(mailId);
        otpEntity.setOtp(encodedOtp);
        otpEntity.setExpiry(LocalDateTime.now().plusMinutes(10));
        otpRepo.save(otpEntity);
        executorService.submit(()->mailService.sendOtpMail(mailId,String.valueOf(random)));
        logger.info("{} OTP sent successfully: {}",mailId, encodedOtp);
    }

}