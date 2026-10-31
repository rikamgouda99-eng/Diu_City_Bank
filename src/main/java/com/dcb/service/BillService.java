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

    public Map<String, List<String>> options() {
        return Map.of(
                "billTypes", Arrays.stream(BillType.values()).map(Enum::name).toList(),
                "paymentModes", Arrays.stream(PaymentMode.values()).map(Enum::name).toList());
    }

    public BillPaymentResponse pay(String bankId, BillPaymentRequest request, String idempotencyKey) {
        transactionService.validateAmount(request.getAmount());
        idempotencyService.validateKey(idempotencyKey);
        boolean cardMode = request.getPaymentMode() == PaymentMode.DEBIT_CARD || request.getPaymentMode() == PaymentMode.CREDIT_CARD;
        if (cardMode && (request.getCardId() == null || request.getCardPin() == null)) {
            throw new BankException("Card and card PIN are mandatory for card payments.");
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
            String billerLabel = request.getBillType().name().replace('_', ' ') + " bill "
                    + request.getConsumerNumber() + (Util.blankToNull(request.getBillerName()) == null ? "" : " (" + request.getBillerName().trim() + ")");

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
