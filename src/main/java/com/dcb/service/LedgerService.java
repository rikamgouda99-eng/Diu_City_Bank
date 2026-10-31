package com.dcb.service;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.CardEntity;
import com.dcb.entity.CardTransactionEntity;
import com.dcb.entity.TransactionStatusEntity;
import com.dcb.enums.CardTransactionType;
import com.dcb.enums.EntryDirection;
import com.dcb.enums.TransactionStatus;
import com.dcb.enums.TransactionType;
import com.dcb.exception.InsufficientFundsException;
import com.dcb.model.TransactionResponse;
import com.dcb.repository.BankAccountRepo;
import com.dcb.repository.CardRepo;
import com.dcb.repository.CardTransactionRepo;
import com.dcb.repository.TransactionRepo;
import com.dcb.util.Util;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The only place account balances change. Every posting updates the (already locked)
 * account and writes a ledger row with the balance before and after.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionRepo txnRepo;
    private final BankAccountRepo accountRepo;
    private final CardRepo cardRepo;
    private final CardTransactionRepo cardTxnRepo;

    @Builder
    public record Posting(TransactionType type, BigDecimal amount, String reference, String description,
                          String counterpartyAccount, String fromBankId, String toBankId, String channel) {
    }

    /** Caller must hold a PESSIMISTIC_WRITE lock on the account. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionStatusEntity debit(BankAccountEntity account, Posting posting) {
        BigDecimal before = account.getAccountBalance() == null ? BigDecimal.ZERO : account.getAccountBalance();
        if (before.compareTo(posting.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient balance. Available balance is " + before.toPlainString() + ".",
                    account.getAccountNo(), posting.type(), posting.amount(), posting.description());
        }
        return post(account, EntryDirection.DEBIT, before, before.subtract(posting.amount()), posting);
    }

    /** Caller must hold a PESSIMISTIC_WRITE lock on the account. */
    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionStatusEntity credit(BankAccountEntity account, Posting posting) {
        BigDecimal before = account.getAccountBalance() == null ? BigDecimal.ZERO : account.getAccountBalance();
        return post(account, EntryDirection.CREDIT, before, before.add(posting.amount()), posting);
    }

    private TransactionStatusEntity post(BankAccountEntity account, EntryDirection direction,
                                         BigDecimal before, BigDecimal after, Posting posting) {
        LocalDateTime now = LocalDateTime.now();
        account.setAccountBalance(after);
        account.setUpdatedAt(now);
        accountRepo.save(account);

        TransactionStatusEntity entry = TransactionStatusEntity.builder()
                .transactionId(Util.generateReference("TXN"))
                .reference(posting.reference())
                .accountNo(account.getAccountNo())
                .fromBankId(posting.fromBankId())
                .toBankId(posting.toBankId())
                .counterpartyAccount(posting.counterpartyAccount())
                .type(posting.type())
                .direction(direction)
                .transactionAmount(posting.amount())
                .balanceBefore(before)
                .balanceAfter(after)
                .status(TransactionStatus.SUCCESS)
                .description(posting.description())
                .channel(posting.channel())
                .rrn(Util.generateRrn())
                .stan(Util.generateStan())
                .transactionDate(now.toLocalDate())
                .updateDate(now.toLocalDate())
                .createdAt(now)
                .updatedAt(now)
                .build();
        TransactionStatusEntity saved = txnRepo.save(entry);
        log.info("{} {} {} {} ref={} balance {} -> {}", account.getAccountNo(), direction, posting.type(),
                posting.amount(), posting.reference(), before, after);
        return saved;
    }

    /** Writes a FAILED record for a rejected debit. Runs in its own transaction after the original one rolled back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFailed(InsufficientFundsException e) {
        LocalDateTime now = LocalDateTime.now();
        if (e.getCardId() != null) {
            CardEntity card = cardRepo.findById(e.getCardId()).orElse(null);
            if (card == null) return;
            cardTxnRepo.save(CardTransactionEntity.builder()
                    .reference(Util.generateReference("CTX"))
                    .cardId(card.getCardId())
                    .cardType(card.getCardType())
                    .type(e.getType() == TransactionType.BILL_PAYMENT ? CardTransactionType.BILL_PAYMENT : CardTransactionType.PURCHASE)
                    .merchantName(e.getDescription() == null ? "Unknown" : e.getDescription())
                    .amount(e.getAmount())
                    .status(TransactionStatus.FAILED)
                    .failureReason(e.getMessage())
                    .createdAt(now)
                    .build());
            return;
        }
        BankAccountEntity account = accountRepo.findById(e.getAccountNo()).orElse(null);
        if (account == null) return;
        txnRepo.save(TransactionStatusEntity.builder()
                .transactionId(Util.generateReference("TXN"))
                .reference(Util.generateReference("FLD"))
                .accountNo(account.getAccountNo())
                .type(e.getType())
                .direction(EntryDirection.DEBIT)
                .transactionAmount(e.getAmount())
                .balanceBefore(account.getAccountBalance())
                .balanceAfter(account.getAccountBalance())
                .status(TransactionStatus.FAILED)
                .description((e.getDescription() == null ? "" : e.getDescription() + " - ") + e.getMessage())
                .channel("SYSTEM")
                .rrn(Util.generateRrn())
                .stan(Util.generateStan())
                .transactionDate(now.toLocalDate())
                .updateDate(LocalDate.now())
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    public static TransactionResponse toResponse(TransactionStatusEntity t) {
        return TransactionResponse.builder()
                .transactionId(t.getTransactionId())
                .reference(t.getReference())
                .accountNo(t.getAccountNo())
                .type(t.getType().name())
                .direction(t.getDirection().name())
                .amount(t.getTransactionAmount())
                .balanceBefore(t.getBalanceBefore())
                .balanceAfter(t.getBalanceAfter())
                .status(t.getStatus().name())
                .description(t.getDescription())
                .counterpartyAccount(Util.maskAccount(t.getCounterpartyAccount()))
                .channel(t.getChannel())
                .rrn(t.getRrn())
                .stan(t.getStan())
                .createdAt(t.getCreatedAt())
                .simulated(true)
                .build();
    }
}
