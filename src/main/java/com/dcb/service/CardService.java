package com.dcb.service;

import com.dcb.config.BankProperties;
import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.CardEntity;
import com.dcb.entity.CardTransactionEntity;
import com.dcb.entity.TransactionStatusEntity;
import com.dcb.entity.UserEntity;
import com.dcb.enums.*;
import com.dcb.exception.BankException;
import com.dcb.exception.InsufficientFundsException;
import com.dcb.exception.NotFoundException;
import com.dcb.model.*;
import com.dcb.repository.CardRepo;
import com.dcb.repository.CardTransactionRepo;
import com.dcb.util.CardNumberCipher;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Simulated debit and credit cards. CVVs are generated at issuance and only their BCrypt hash is kept;
 * the full card number is stored AES encrypted so the owner can reveal it. Credit card payments
 * are confirmed with the card PIN and an OTP mailed to the owner.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardService {

    private static final Set<CardStatus> OPEN_STATUSES = EnumSet.of(CardStatus.APPLIED, CardStatus.ACTIVE, CardStatus.BLOCKED);
    private static final Set<String> WEAK_PINS = Set.of("0000", "1111", "2222", "3333", "4444", "5555",
            "6666", "7777", "8888", "9999", "1234", "4321", "0123", "9876");
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("MM/yy");

    private final CardRepo cardRepo;
    private final CardTransactionRepo cardTxnRepo;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final FinancialExecutor executor;
    private final PasswordEncoder passwordEncoder;
    private final BankProperties props;
    private final CardOtpService cardOtpService;
    private final CardNumberCipher cardNumberCipher;

    // ------------------------------------------------------------------ customer

    @Transactional
    public CardResponse apply(String bankId, CardApplyRequest request) {
        UserEntity user = accountService.getUser(bankId);
        accountService.assertUserActive(user);
        BankAccountEntity account = accountService.getAccount(bankId);
        accountService.assertActive(account, "Your account");

        if (cardRepo.existsByBankIdAndCardTypeAndStatusIn(bankId, request.getCardType(), OPEN_STATUSES)) {
            throw new BankException("You already have a " + request.getCardType().name().toLowerCase()
                    + " card or a pending application. Use replacement for a new card.");
        }
        BigDecimal requestedLimit = null;
        if (request.getCardType() == CardType.CREDIT) {
            requestedLimit = request.getRequestedLimit() == null ? props.getCard().getDefaultCreditLimit() : request.getRequestedLimit();
            if (requestedLimit.compareTo(props.getCard().getMaxCreditLimit()) > 0) {
                throw new BankException("Requested credit limit can be at most " + props.getCard().getMaxCreditLimit().toPlainString() + ".");
            }
        }
        CardEntity card = cardRepo.save(CardEntity.builder()
                .cardType(request.getCardType())
                .cardScheme(request.getCardScheme())
                .status(CardStatus.APPLIED)
                .bankId(bankId)
                .accountNo(account.getAccountNo())
                .holderName(Util.fullName(user).toUpperCase())
                .requestedLimit(requestedLimit)
                .appliedAt(LocalDateTime.now())
                .build());
        log.info("{} applied for {} {} card, application {}", bankId, request.getCardScheme(), request.getCardType(), card.getCardId());
        return toResponse(card);
    }

    public List<CardResponse> list(String bankId) {
        return cardRepo.findByBankIdOrderByAppliedAtDesc(bankId).stream().map(this::toResponse).toList();
    }

    public CardResponse get(String bankId, Long cardId) {
        return toResponse(own(bankId, cardId));
    }

    public CardLimitResponse limit(String bankId, Long cardId) {
        CardEntity card = own(bankId, cardId);
        requireStatus(card, CardStatus.ACTIVE, CardStatus.BLOCKED);
        boolean debit = card.getCardType() == CardType.DEBIT;
        BigDecimal used = debit ? spentToday(card) : nz(card.getCreditUsed());
        return CardLimitResponse.builder()
                .cardId(card.getCardId())
                .cardType(card.getCardType().name())
                .limitType(debit ? "DAILY_SPEND_LIMIT" : "CREDIT_LIMIT")
                .cardLimit(card.getCardLimit())
                .used(used)
                .available(card.getCardLimit().subtract(used).max(BigDecimal.ZERO))
                .build();
    }

    /**
     * Full card number for the owner's show/hide toggle. Cards issued before full numbers were stored only
     * kept their last four digits, so they get a full number that keeps those digits (and the scheme prefix)
     * the first time it is asked for, and it is stored encrypted from then on.
     */
    @Transactional
    public Map<String, String> revealNumber(String bankId, Long cardId) {
        CardEntity card = lockOwn(bankId, cardId);
        requireStatus(card, CardStatus.ACTIVE, CardStatus.BLOCKED);
        if (card.getPanEncrypted() == null) {
            String last4 = card.getMaskedNumber().substring(card.getMaskedNumber().length() - 4);
            card.setPanEncrypted(cardNumberCipher.encrypt(panEndingWith(card.getCardScheme(), last4)));
            card.setUpdatedAt(LocalDateTime.now());
            cardRepo.save(card);
            log.info("{} full number created for card {} issued before numbers were stored", bankId, cardId);
        }
        String pan = cardNumberCipher.decrypt(card.getPanEncrypted());
        log.info("{} viewed the full number of card {}", bankId, cardId);
        return Map.of("cardNumber", pan.replaceAll("(\\d{4})(?=\\d)", "$1 "));
    }

    /** Mails an OTP that confirms the next payment on this credit card. */
    public void sendPaymentOtp(String bankId, Long cardId) {
        CardEntity card = own(bankId, cardId);
        if (card.getCardType() != CardType.CREDIT) {
            throw new BankException("OTP is only needed for credit card payments.");
        }
        requireStatus(card, CardStatus.ACTIVE, CardStatus.BLOCKED, CardStatus.REPLACED);
        cardOtpService.send(card);
    }

    /**
     * A credit card payment needs the card PIN and the mailed OTP. The PIN is checked first, so a
     * mistyped PIN doesn't use up the OTP. Debit cards are left to the PIN check in lockUsableCard.
     */
    public void confirmCreditPayment(String bankId, Long cardId, String pin, String otp) {
        CardEntity card = own(bankId, cardId);
        if (card.getCardType() != CardType.CREDIT) return;
        if (card.getPinHash() == null) {
            throw new BankException("Please set a PIN for this card before using it.");
        }
        if (pin == null || !passwordEncoder.matches(pin, card.getPinHash())) {
            throw new BankException("Incorrect card PIN.");
        }
        cardOtpService.verify(card.getCardId(), otp);
    }

    @Transactional
    public CardResponse changePin(String bankId, Long cardId, PinChangeRequest request) {
        CardEntity card = lockOwn(bankId, cardId);
        requireStatus(card, CardStatus.ACTIVE);
        if (card.getPinHash() != null) {
            if (request.getOldPin() == null || !passwordEncoder.matches(request.getOldPin(), card.getPinHash())) {
                throw new BankException("Current PIN is incorrect.");
            }
            if (request.getOldPin().equals(request.getNewPin())) {
                throw new BankException("New PIN must be different from the current PIN.");
            }
        }
        if (WEAK_PINS.contains(request.getNewPin())) {
            throw new BankException("PIN is too easy to guess, please choose another.");
        }
        card.setPinHash(passwordEncoder.encode(request.getNewPin()));
        card.setUpdatedAt(LocalDateTime.now());
        log.info("{} PIN changed for card {}", bankId, cardId);
        return toResponse(cardRepo.save(card));
    }

    @Transactional
    public CardResponse block(String bankId, Long cardId) {
        CardEntity card = lockOwn(bankId, cardId);
        requireStatus(card, CardStatus.ACTIVE);
        card.setStatus(CardStatus.BLOCKED);
        card.setBlockedBy("CUSTOMER");
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepo.save(card));
    }

    @Transactional
    public CardResponse unblock(String bankId, Long cardId) {
        CardEntity card = lockOwn(bankId, cardId);
        requireStatus(card, CardStatus.BLOCKED);
        if ("ADMIN".equals(card.getBlockedBy())) {
            throw new BankException("This card was blocked by the bank, please contact the bank to unblock it.");
        }
        card.setStatus(CardStatus.ACTIVE);
        card.setBlockedBy(null);
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepo.save(card));
    }

    /** Blocks the current card and opens an application for a replacement that the admin issues. */
    @Transactional
    public CardResponse requestReplacement(String bankId, Long cardId) {
        CardEntity card = lockOwn(bankId, cardId);
        requireStatus(card, CardStatus.ACTIVE, CardStatus.BLOCKED);
        boolean pending = cardRepo.findByBankIdOrderByAppliedAtDesc(bankId).stream()
                .anyMatch(c -> c.getStatus() == CardStatus.APPLIED && c.getCardType() == card.getCardType());
        if (pending) {
            throw new BankException("A " + card.getCardType().name().toLowerCase() + " card application is already pending.");
        }
        if (card.getStatus() == CardStatus.ACTIVE) {
            card.setStatus(CardStatus.BLOCKED);
            card.setBlockedBy("CUSTOMER");
            card.setUpdatedAt(LocalDateTime.now());
            cardRepo.save(card);
        }
        CardEntity replacement = cardRepo.save(CardEntity.builder()
                .cardType(card.getCardType())
                .cardScheme(card.getCardScheme())
                .status(CardStatus.APPLIED)
                .bankId(bankId)
                .accountNo(card.getAccountNo())
                .holderName(card.getHolderName())
                .requestedLimit(card.getCardLimit())
                .replacementOfCardId(card.getCardId())
                .appliedAt(LocalDateTime.now())
                .build());
        return toResponse(replacement);
    }

    public CardTransactionResponse simulate(String bankId, Long cardId, CardSimulationRequest request, String idempotencyKey) {
        validateAmount(request.getAmount());
        idempotencyService.validateKey(idempotencyKey);
        confirmCreditPayment(bankId, cardId, request.getPin(), request.getOtp());
        return executor.run(() -> {
            // lock order everywhere: account first, then card
            BankAccountEntity account = accountService.lockOperableAccount(bankId);
            CardEntity card = lockUsableCard(bankId, cardId, null, request.getPin());

            var prior = idempotencyService.findResult(bankId, idempotencyKey, "CARD_TXN");
            if (prior.isPresent()) return replay(prior.get());

            String reference = Util.generateReference("CTX");
            CardTransactionEntity txn = charge(card, account, request.getAmount(), request.getMerchantName().trim(),
                    CardTransactionType.PURCHASE, reference);
            idempotencyService.remember(bankId, idempotencyKey, "CARD_TXN", reference);
            return toTxnResponse(txn, card);
        });
    }

    public CardTransactionResponse payDues(String bankId, Long cardId, CardRepaymentRequest request, String idempotencyKey) {
        validateAmount(request.getAmount());
        idempotencyService.validateKey(idempotencyKey);
        confirmCreditPayment(bankId, cardId, request.getPin(), request.getOtp());
        return executor.run(() -> {
            BankAccountEntity account = accountService.lockOperableAccount(bankId);
            CardEntity card = lockOwn(bankId, cardId);
            if (card.getCardType() != CardType.CREDIT) {
                throw new BankException("Only credit card dues can be paid.");
            }
            requireStatus(card, CardStatus.ACTIVE, CardStatus.BLOCKED, CardStatus.REPLACED);

            var prior = idempotencyService.findResult(bankId, idempotencyKey, "CARD_REPAYMENT");
            if (prior.isPresent()) return replay(prior.get());

            BigDecimal outstanding = nz(card.getCreditUsed());
            if (outstanding.signum() == 0) {
                throw new BankException("There are no dues on this card.");
            }
            if (request.getAmount().compareTo(outstanding) > 0) {
                throw new BankException("Amount is more than the outstanding dues of " + outstanding.toPlainString() + ".");
            }
            String reference = Util.generateReference("CRP");
            TransactionStatusEntity ledger = ledgerService.debit(account, LedgerService.Posting.builder()
                    .type(TransactionType.CARD_BILL_PAYMENT)
                    .amount(request.getAmount())
                    .reference(reference)
                    .description("Credit card dues payment (" + card.getMaskedNumber() + ")")
                    .fromBankId(bankId)
                    .channel("CARD_SIM")
                    .build());
            card.setCreditUsed(outstanding.subtract(request.getAmount()));
            card.setUpdatedAt(LocalDateTime.now());
            cardRepo.save(card);
            CardTransactionEntity txn = cardTxnRepo.save(CardTransactionEntity.builder()
                    .reference(reference)
                    .cardId(card.getCardId())
                    .cardType(card.getCardType())
                    .type(CardTransactionType.REPAYMENT)
                    .merchantName("Card dues payment")
                    .amount(request.getAmount())
                    .status(TransactionStatus.SUCCESS)
                    .ledgerTransactionId(ledger.getTransactionId())
                    .createdAt(LocalDateTime.now())
                    .build());
            idempotencyService.remember(bankId, idempotencyKey, "CARD_REPAYMENT", reference);
            return toTxnResponse(txn, card);
        });
    }

    public PageResponse<CardTransactionResponse> transactions(String bankId, Long cardId, int page, int size) {
        CardEntity card = own(bankId, cardId);
        return PageResponse.of(cardTxnRepo.findByCardIdOrderByCreatedAtDesc(cardId, Util.page(page, size)),
                t -> toTxnResponse(t, card));
    }

    // ------------------------------------------------------------------ used by bill payments

    /**
     * Locks the customer's card and checks it can be charged: owned, ACTIVE, not expired, PIN set and correct.
     * Caller must already hold the account lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CardEntity lockUsableCard(String bankId, Long cardId, CardType expectedType, String pin) {
        CardEntity card = lockOwn(bankId, cardId);
        if (expectedType != null && card.getCardType() != expectedType) {
            throw new BankException("Selected card is not a " + expectedType.name().toLowerCase() + " card.");
        }
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BankException("Card is " + card.getStatus().name().toLowerCase() + ", it can't be used.");
        }
        if (card.getValidUpto() != null && card.getValidUpto().isBefore(LocalDate.now())) {
            throw new BankException("Card has expired.");
        }
        if (card.getPinHash() == null) {
            throw new BankException("Please set a PIN for this card before using it.");
        }
        if (pin == null || !passwordEncoder.matches(pin, card.getPinHash())) {
            throw new BankException("Incorrect card PIN.");
        }
        return card;
    }

    /** Charges a locked card: debit cards take money from the locked account, credit cards use the credit line. */
    @Transactional(propagation = Propagation.MANDATORY)
    public CardTransactionEntity charge(CardEntity card, BankAccountEntity account, BigDecimal amount, String merchant,
                                        CardTransactionType type, String reference) {
        TransactionType ledgerType = type == CardTransactionType.BILL_PAYMENT ? TransactionType.BILL_PAYMENT : TransactionType.CARD_PAYMENT;
        String ledgerId = null;
        if (card.getCardType() == CardType.DEBIT) {
            BigDecimal availableToday = card.getCardLimit().subtract(spentToday(card));
            if (amount.compareTo(availableToday) > 0) {
                throw new InsufficientFundsException("Daily card limit exceeded. Available today: " + availableToday.max(BigDecimal.ZERO).toPlainString() + ".",
                        account.getAccountNo(), ledgerType, amount, merchant, card.getCardId());
            }
            if (account.getAccountBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient account balance for this card payment.",
                        account.getAccountNo(), ledgerType, amount, merchant, card.getCardId());
            }
            TransactionStatusEntity ledger = ledgerService.debit(account, LedgerService.Posting.builder()
                    .type(ledgerType)
                    .amount(amount)
                    .reference(reference)
                    .description("Debit card " + card.getMaskedNumber() + " at " + merchant)
                    .fromBankId(card.getBankId())
                    .channel("CARD_SIM")
                    .build());
            ledgerId = ledger.getTransactionId();
        } else {
            BigDecimal available = card.getCardLimit().subtract(nz(card.getCreditUsed()));
            if (amount.compareTo(available) > 0) {
                throw new InsufficientFundsException("Insufficient credit limit. Available credit: " + available.max(BigDecimal.ZERO).toPlainString() + ".",
                        account.getAccountNo(), ledgerType, amount, merchant, card.getCardId());
            }
            card.setCreditUsed(nz(card.getCreditUsed()).add(amount));
            card.setUpdatedAt(LocalDateTime.now());
            cardRepo.save(card);
        }
        return cardTxnRepo.save(CardTransactionEntity.builder()
                .reference(reference)
                .cardId(card.getCardId())
                .cardType(card.getCardType())
                .type(type)
                .merchantName(merchant)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .ledgerTransactionId(ledgerId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    // ------------------------------------------------------------------ admin

    public PageResponse<CardResponse> adminList(CardStatus status, CardType type, int page, int size) {
        return PageResponse.of(cardRepo.search(status, type, Util.page(page, size)), this::toResponse);
    }

    public CardResponse adminGet(Long cardId) {
        return toResponse(find(cardId));
    }

    @Transactional
    public CardResponse approve(Long cardId, CardApprovalRequest request) {
        CardEntity card = cardRepo.lockById(cardId).orElseThrow(() -> new NotFoundException("Card not found."));
        requireStatus(card, CardStatus.APPLIED);

        BigDecimal limit;
        if (card.getCardType() == CardType.CREDIT) {
            limit = request.getCardLimit() != null ? request.getCardLimit()
                    : card.getRequestedLimit() != null ? card.getRequestedLimit() : props.getCard().getDefaultCreditLimit();
            if (limit.compareTo(props.getCard().getMaxCreditLimit()) > 0) {
                throw new BankException("Credit limit can be at most " + props.getCard().getMaxCreditLimit().toPlainString() + ".");
            }
        } else {
            limit = request.getCardLimit() != null ? request.getCardLimit() : props.getCard().getDebitDailyLimit();
            if (limit.compareTo(props.getCard().getMaxDebitDailyLimit()) > 0) {
                throw new BankException("Debit card daily limit can be at most " + props.getCard().getMaxDebitDailyLimit().toPlainString() + ".");
            }
        }

        BigDecimal carriedDues = BigDecimal.ZERO;
        if (card.getReplacementOfCardId() != null) {
            CardEntity old = cardRepo.lockById(card.getReplacementOfCardId()).orElse(null);
            if (old != null) {
                carriedDues = nz(old.getCreditUsed());
                old.setStatus(CardStatus.REPLACED);
                old.setCreditUsed(BigDecimal.ZERO);
                old.setUpdatedAt(LocalDateTime.now());
                cardRepo.save(old);
            }
        }

        String pan = generatePan(card.getCardScheme());
        card.setMaskedNumber("XXXX XXXX XXXX " + pan.substring(12));
        card.setPanEncrypted(cardNumberCipher.encrypt(pan));
        card.setCardToken(UUID.randomUUID().toString());
        card.setCvvHash(passwordEncoder.encode(Util.randomDigits(3)));
        card.setCardLimit(limit);
        card.setCreditUsed(card.getCardType() == CardType.CREDIT ? carriedDues : null);
        card.setIssuedDate(LocalDate.now());
        card.setValidUpto(LocalDate.now().plusYears(props.getCard().getValidityYears()).withDayOfMonth(1).plusMonths(1).minusDays(1));
        card.setStatus(CardStatus.ACTIVE);
        card.setAdminComment(Util.blankToNull(request.getComment()));
        card.setUpdatedAt(LocalDateTime.now());
        log.info("Card application {} approved, issued {} with limit {}", cardId, card.getMaskedNumber(), limit);
        return toResponse(cardRepo.save(card));
    }

    @Transactional
    public CardResponse reject(Long cardId, DecisionRequest request) {
        CardEntity card = cardRepo.lockById(cardId).orElseThrow(() -> new NotFoundException("Card not found."));
        requireStatus(card, CardStatus.APPLIED);
        String comment = Util.blankToNull(request == null ? null : request.getComment());
        if (comment == null) {
            throw new BankException("Please give a reason for rejecting the application.");
        }
        card.setStatus(CardStatus.REJECTED);
        card.setAdminComment(comment);
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepo.save(card));
    }

    @Transactional
    public CardResponse adminBlock(Long cardId) {
        CardEntity card = cardRepo.lockById(cardId).orElseThrow(() -> new NotFoundException("Card not found."));
        requireStatus(card, CardStatus.ACTIVE, CardStatus.BLOCKED);
        card.setStatus(CardStatus.BLOCKED);
        card.setBlockedBy("ADMIN");
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepo.save(card));
    }

    @Transactional
    public CardResponse adminUnblock(Long cardId) {
        CardEntity card = cardRepo.lockById(cardId).orElseThrow(() -> new NotFoundException("Card not found."));
        requireStatus(card, CardStatus.BLOCKED);
        card.setStatus(CardStatus.ACTIVE);
        card.setBlockedBy(null);
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepo.save(card));
    }

    @Transactional
    public CardResponse updateLimit(Long cardId, CardLimitUpdateRequest request) {
        CardEntity card = cardRepo.lockById(cardId).orElseThrow(() -> new NotFoundException("Card not found."));
        requireStatus(card, CardStatus.ACTIVE, CardStatus.BLOCKED);
        BigDecimal limit = request.getCardLimit();
        if (card.getCardType() == CardType.CREDIT) {
            if (limit.compareTo(props.getCard().getMaxCreditLimit()) > 0) {
                throw new BankException("Credit limit can be at most " + props.getCard().getMaxCreditLimit().toPlainString() + ".");
            }
            if (limit.compareTo(nz(card.getCreditUsed())) < 0) {
                throw new BankException("Credit limit can't be lower than the current outstanding of " + nz(card.getCreditUsed()).toPlainString() + ".");
            }
        } else if (limit.compareTo(props.getCard().getMaxDebitDailyLimit()) > 0) {
            throw new BankException("Debit card daily limit can be at most " + props.getCard().getMaxDebitDailyLimit().toPlainString() + ".");
        }
        card.setCardLimit(limit);
        card.setUpdatedAt(LocalDateTime.now());
        return toResponse(cardRepo.save(card));
    }

    public PageResponse<CardTransactionResponse> adminTransactions(Long cardId, int page, int size) {
        CardEntity card = find(cardId);
        return PageResponse.of(cardTxnRepo.findByCardIdOrderByCreatedAtDesc(cardId, Util.page(page, size)),
                t -> toTxnResponse(t, card));
    }

    // ------------------------------------------------------------------ helpers

    private CardEntity find(Long cardId) {
        return cardRepo.findById(cardId).orElseThrow(() -> new NotFoundException("Card not found."));
    }

    private CardEntity own(String bankId, Long cardId) {
        return cardRepo.findByCardIdAndBankId(cardId, bankId).orElseThrow(() -> new NotFoundException("Card not found."));
    }

    private CardEntity lockOwn(String bankId, Long cardId) {
        CardEntity card = cardRepo.lockById(cardId).orElseThrow(() -> new NotFoundException("Card not found."));
        if (!card.getBankId().equals(bankId)) {
            throw new NotFoundException("Card not found.");
        }
        return card;
    }

    private void requireStatus(CardEntity card, CardStatus... allowed) {
        for (CardStatus s : allowed) {
            if (card.getStatus() == s) return;
        }
        throw new BankException("This action isn't allowed while the card is " + card.getStatus().name().toLowerCase() + ".");
    }

    private BigDecimal spentToday(CardEntity card) {
        return nz(cardTxnRepo.sumSpendSince(card.getCardId(), LocalDate.now().atStartOfDay()));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BankException("Amount must be greater than zero.");
        }
        if (amount.compareTo(props.getTxn().getMaxAmount()) > 0) {
            throw new BankException("Maximum amount per transaction is " + props.getTxn().getMaxAmount().toPlainString() + ".");
        }
    }

    private CardTransactionResponse replay(String reference) {
        CardTransactionEntity txn = cardTxnRepo.findByReference(reference).orElseThrow();
        CardTransactionResponse response = toTxnResponse(txn, find(txn.getCardId()));
        response.setReplayed(true);
        return response;
    }

    /** 16 digit Luhn-valid number for the scheme that ends with the given four digits. */
    static String panEndingWith(CardScheme scheme, String last4) {
        String prefix = schemePrefix(scheme);
        char[] digits = (prefix + Util.randomDigits(12 - prefix.length()) + last4).toCharArray();
        // changing one digit in front of the last four shifts the Luhn sum through every value mod 10,
        // so exactly one value of it makes the whole number valid
        int fix = 11;
        for (int d = 0; d <= 9; d++) {
            digits[fix] = (char) ('0' + d);
            if (luhnValid(new String(digits))) break;
        }
        return new String(digits);
    }

    static boolean luhnValid(String number) {
        int sum = 0;
        for (int i = 0; i < number.length(); i++) {
            int digit = number.charAt(number.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }

    private static String schemePrefix(CardScheme scheme) {
        return switch (scheme) {
            case VISA -> "4";
            case MASTERCARD -> "5" + (1 + Integer.parseInt(Util.randomDigits(1)) % 5);
            case RUPAY -> "60";
        };
    }

    /** 16 digit number with a valid Luhn check digit. */
    static String generatePan(CardScheme scheme) {
        String prefix = schemePrefix(scheme);
        String body = prefix + Util.randomDigits(15 - prefix.length());
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int digit = body.charAt(body.length() - 1 - i) - '0';
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
        }
        return body + ((10 - sum % 10) % 10);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public CardResponse toResponse(CardEntity c) {
        boolean issued = c.getIssuedDate() != null;
        CardResponse.CardResponseBuilder b = CardResponse.builder()
                .cardId(c.getCardId())
                .bankId(c.getBankId())
                .maskedNumber(c.getMaskedNumber())
                .cardType(c.getCardType().name())
                .cardScheme(c.getCardScheme().name())
                .holderName(c.getHolderName())
                .expiry(c.getValidUpto() == null ? null : c.getValidUpto().format(EXPIRY))
                .linkedAccount(Util.maskAccount(c.getAccountNo()))
                .cardLimit(c.getCardLimit())
                .requestedLimit(c.getRequestedLimit())
                .status(c.getStatus().name())
                .blockedBy(c.getBlockedBy())
                .pinSet(c.getPinHash() != null)
                .issuedDate(c.getIssuedDate())
                .appliedAt(c.getAppliedAt())
                .adminComment(c.getAdminComment())
                .replacementOfCardId(c.getReplacementOfCardId())
                .simulated(true);
        if (issued && c.getCardType() == CardType.CREDIT) {
            BigDecimal used = nz(c.getCreditUsed());
            b.creditUsed(used).availableCredit(c.getCardLimit().subtract(used).max(BigDecimal.ZERO)).billing(billing(c, LocalDate.now()));
        } else if (issued) {
            BigDecimal spent = spentToday(c);
            b.spentToday(spent).availableToday(c.getCardLimit().subtract(spent).max(BigDecimal.ZERO));
        }
        return b.build();
    }

    /**
     * Billing cycle of a credit card on a given day. A statement is generated on the statement day every
     * month and must be paid within paymentDueDays. While the latest statement is still open (generated
     * after the card was issued and its due date not yet passed) it is the one shown; otherwise the next
     * statement and its due date are shown. A statement from before the card was issued is never shown.
     */
    public CardBillingResponse billing(CardEntity card, LocalDate today) {
        BigDecimal outstanding = nz(card.getCreditUsed());
        int day = Math.min(Math.max(props.getCard().getStatementDay(), 1), 28);
        int dueDays = props.getCard().getPaymentDueDays();
        LocalDate latest = today.getDayOfMonth() >= day ? today.withDayOfMonth(day) : today.minusMonths(1).withDayOfMonth(day);
        LocalDate next = latest.plusMonths(1);
        boolean latestIsOpen = card.getIssuedDate() != null && latest.isAfter(card.getIssuedDate())
                && !today.isAfter(latest.plusDays(dueDays));
        LocalDate statement = latestIsOpen ? latest : next;
        BigDecimal minimumDue = outstanding.multiply(props.getCard().getMinimumDuePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .max(outstanding.min(new BigDecimal("100.00")));
        return CardBillingResponse.builder()
                .totalOutstanding(outstanding)
                .minimumDue(minimumDue)
                .statementDate(statement)
                .paymentDueDate(statement.plusDays(dueDays))
                .nextStatementDate(next)
                .daysToDue(java.time.temporal.ChronoUnit.DAYS.between(today, statement.plusDays(dueDays)))
                .build();
    }

    private CardTransactionResponse toTxnResponse(CardTransactionEntity t, CardEntity card) {
        BigDecimal availableAfter = card.getCardLimit() == null ? null : card.getCardType() == CardType.CREDIT
                ? card.getCardLimit().subtract(nz(card.getCreditUsed())) : null;
        return CardTransactionResponse.builder()
                .id(t.getId())
                .reference(t.getReference())
                .cardId(t.getCardId())
                .maskedNumber(card.getMaskedNumber())
                .cardType(t.getCardType().name())
                .type(t.getType().name())
                .merchantName(t.getMerchantName())
                .amount(t.getAmount())
                .status(t.getStatus().name())
                .ledgerTransactionId(t.getLedgerTransactionId())
                .failureReason(t.getFailureReason())
                .availableAfter(availableAfter)
                .createdAt(t.getCreatedAt())
                .simulated(true)
                .build();
    }
}
