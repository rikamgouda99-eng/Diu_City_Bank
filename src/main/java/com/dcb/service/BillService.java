package com.dcb.service;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.BillPaymentEntity;
import com.dcb.entity.CardEntity;
import com.dcb.entity.CardTransactionEntity;
import com.dcb.entity.TransactionStatusEntity;
import com.dcb.enums.*;
import com.dcb.exception.BankException;
import com.dcb.model.BillPaymentRequest;
import com.dcb.model.BillPaymentResponse;
import com.dcb.model.PageResponse;
import com.dcb.repository.BillPaymentRepo;
import com.dcb.repository.CardRepo;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Simulated utility bill payments. Nothing is sent to a real biller or payment gateway. */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillService {

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final CardService cardService;
    private final IdempotencyService idempotencyService;
    private final FinancialExecutor executor;
    private final TransactionService transactionService;
    private final BillPaymentRepo billRepo;
    private final CardRepo cardRepo;

    /** What each bill type asks for: the number to pay against and who the provider is. */
    public record Biller(String numberLabel, String numberPattern, String numberHint, String providerLabel, List<String> providers) {
    }

    private static final Map<BillType, Biller> BILLERS = new EnumMap<>(Map.of(
            BillType.ELECTRICITY, new Biller("Consumer number", "[A-Za-z0-9\\-]{6,20}", "6-20 letters, digits or hyphens",
                    "Electricity board", List.of("Electricity Department Diu", "Gujarat Urja Vikas Nigam (GUVNL)", "Tata Power", "Adani Electricity", "BSES Rajdhani", "MSEDCL")),
            BillType.WATER, new Biller("Connection number", "[A-Za-z0-9\\-]{5,20}", "5-20 letters, digits or hyphens",
                    "Water board", List.of("Diu Municipal Council", "Delhi Jal Board", "Mumbai Municipal Corporation", "Bangalore Water Supply (BWSSB)", "Hyderabad Metro Water Board")),
            BillType.MOBILE_RECHARGE, new Biller("Mobile number", "[6-9]\\d{9}", "10 digit mobile number",
                    "Network provider", List.of("Jio", "Airtel", "Vi (Vodafone Idea)", "BSNL")),
            BillType.INTERNET, new Biller("Account / customer ID", "[A-Za-z0-9\\-]{4,20}", "4-20 letters, digits or hyphens",
                    "Internet provider", List.of("JioFiber", "Airtel Xstream Fiber", "BSNL Broadband", "ACT Fibernet", "Hathway")),
            BillType.GAS, new Biller("Consumer number", "[A-Za-z0-9\\-]{5,20}", "5-20 letters, digits or hyphens",
                    "Gas provider", List.of("Indane", "Bharat Gas", "HP Gas", "Gujarat Gas", "Adani Total Gas")),
            BillType.DTH, new Biller("Subscriber ID", "\\d{8,12}", "8-12 digit subscriber ID",
                    "DTH operator", List.of("Tata Play", "Airtel Digital TV", "Dish TV", "Sun Direct", "d2h")),
            BillType.OTHER, new Biller("Consumer / reference number", "[A-Za-z0-9\\-]{4,30}", "4-30 letters, digits or hyphens",
                    "Biller name", List.of())));

    /** UPI is no longer offered; old UPI payments stay readable in history. */
    private static final List<PaymentMode> PAYMENT_MODES = List.of(PaymentMode.DEBIT_CARD, PaymentMode.CREDIT_CARD, PaymentMode.BANK_DRAFT);

    public Map<String, Object> options() {
        Map<String, Biller> billers = new LinkedHashMap<>();
        BILLERS.forEach((type, biller) -> billers.put(type.name(), biller));
        return Map.of(
                "billTypes", Arrays.stream(BillType.values()).map(Enum::name).toList(),
                "paymentModes", PAYMENT_MODES.stream().map(Enum::name).toList(),
                "billers", billers);
    }

    /** Checks the number and provider match what the chosen bill type needs. */
    private void validateBiller(BillPaymentRequest request) {
        Biller biller = BILLERS.get(request.getBillType());
        String number = request.getConsumerNumber() == null ? "" : request.getConsumerNumber().trim();
        if (!number.matches(biller.numberPattern())) {
            throw new BankException(biller.numberLabel() + " must be " + biller.numberHint() + ".");
        }
        String provider = Util.blankToNull(request.getBillerName());
        if (provider == null) {
            throw new BankException("Please choose the " + biller.providerLabel().toLowerCase() + ".");
        }
        if (!biller.providers().isEmpty() && !biller.providers().contains(provider.trim())) {
            throw new BankException("Please choose a valid " + biller.providerLabel().toLowerCase() + ".");
        }
    }

    public BillPaymentResponse pay(String bankId, BillPaymentRequest request, String idempotencyKey) {
        transactionService.validateAmount(request.getAmount());
        idempotencyService.validateKey(idempotencyKey);
        if (!PAYMENT_MODES.contains(request.getPaymentMode())) {
            throw new BankException(request.getPaymentMode().name().replace('_', ' ') + " is not available for bill payments.");
        }
        validateBiller(request);
        request.setConsumerNumber(request.getConsumerNumber().trim());
        boolean cardMode = request.getPaymentMode() == PaymentMode.DEBIT_CARD || request.getPaymentMode() == PaymentMode.CREDIT_CARD;
        if (cardMode && (request.getCardId() == null || request.getCardPin() == null)) {
            throw new BankException("Card and card PIN are mandatory for card payments.");
        }
        if (request.getPaymentMode() == PaymentMode.CREDIT_CARD) {
            cardService.confirmCreditPayment(bankId, request.getCardId(), request.getCardPin(), request.getOtp());
        }

        return executor.run(() -> {
            BankAccountEntity account = accountService.lockOperableAccount(bankId);
            var prior = idempotencyService.findResult(bankId, idempotencyKey, "BILL_PAYMENT");
            if (prior.isPresent()) {
                BillPaymentResponse replay = toResponse(billRepo.findByReference(prior.get()).orElseThrow());
                replay.setReplayed(true);
                return replay;
            }

            String reference = Util.generateReference("BIL");
            String billerLabel = request.getBillType() == BillType.MOBILE_RECHARGE
                    ? request.getBillerName().trim() + " recharge " + request.getConsumerNumber()
                    : request.getBillType().name().replace('_', ' ') + " bill " + request.getConsumerNumber() + " (" + request.getBillerName().trim() + ")";

            String transactionId = null;
            Long cardTransactionId = null;
            Long cardId = null;
            switch (request.getPaymentMode()) {
                case UPI, BANK_DRAFT -> {
                    TransactionStatusEntity entry = ledgerService.debit(account, LedgerService.Posting.builder()
                            .type(TransactionType.BILL_PAYMENT)
                            .amount(request.getAmount())
                            .reference(reference)
                            .description(billerLabel + " via " + request.getPaymentMode().name().replace('_', ' '))
                            .fromBankId(bankId)
                            .channel(request.getPaymentMode().name())
                            .build());
                    transactionId = entry.getTransactionId();
                }
                case DEBIT_CARD, CREDIT_CARD -> {
                    CardType type = request.getPaymentMode() == PaymentMode.DEBIT_CARD ? CardType.DEBIT : CardType.CREDIT;
                    CardEntity card = cardService.lockUsableCard(bankId, request.getCardId(), type, request.getCardPin());
                    CardTransactionEntity cardTxn = cardService.charge(card, account, request.getAmount(), billerLabel,
                            CardTransactionType.BILL_PAYMENT, reference);
                    transactionId = cardTxn.getLedgerTransactionId();
                    cardTransactionId = cardTxn.getId();
                    cardId = card.getCardId();
                }
            }

            BillPaymentEntity bill = billRepo.save(BillPaymentEntity.builder()
                    .reference(reference)
                    .bankId(bankId)
                    .accountNo(account.getAccountNo())
                    .billType(request.getBillType())
                    .billerName(Util.blankToNull(request.getBillerName()))
                    .consumerNumber(request.getConsumerNumber())
                    .amount(request.getAmount())
                    .paymentMode(request.getPaymentMode())
                    .transactionId(transactionId)
                    .cardTransactionId(cardTransactionId)
                    .cardId(cardId)
                    .status(TransactionStatus.SUCCESS)
                    .paymentDate(LocalDateTime.now())
                    .build());
            idempotencyService.remember(bankId, idempotencyKey, "BILL_PAYMENT", reference);
            log.info("{} paid {} bill {} of {} via {}", bankId, request.getBillType(), reference, request.getAmount(), request.getPaymentMode());
            return toResponse(bill);
        });
    }

    public PageResponse<BillPaymentResponse> history(String bankId, int page, int size) {
        return PageResponse.of(billRepo.findByBankIdOrderByPaymentDateDesc(bankId, Util.page(page, size)), this::toResponse);
    }

    public PageResponse<BillPaymentResponse> adminSearch(BillType billType, String bankId, int page, int size) {
        return PageResponse.of(billRepo.search(billType, Util.blankToNull(bankId), Util.page(page, size)), this::toResponse);
    }

    private BillPaymentResponse toResponse(BillPaymentEntity b) {
        String masked = b.getCardId() == null ? null
                : cardRepo.findById(b.getCardId()).map(CardEntity::getMaskedNumber).orElse(null);
        return BillPaymentResponse.builder()
                .billId(b.getBillId())
                .reference(b.getReference())
                .bankId(b.getBankId())
                .billType(b.getBillType().name())
                .billerName(b.getBillerName())
                .consumerNumber(b.getConsumerNumber())
                .amount(b.getAmount())
                .paymentMode(b.getPaymentMode().name())
                .transactionId(b.getTransactionId())
                .cardTransactionId(b.getCardTransactionId())
                .cardMasked(masked)
                .status(b.getStatus().name())
                .paymentDate(b.getPaymentDate())
                .simulated(true)
                .build();
    }
}
