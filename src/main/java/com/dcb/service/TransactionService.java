package com.dcb.service;

import com.dcb.config.BankProperties;
import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.TransactionStatusEntity;
import com.dcb.entity.TransferEntity;
import com.dcb.entity.UserEntity;
import com.dcb.enums.TransactionStatus;
import com.dcb.enums.TransactionType;
import com.dcb.exception.BankException;
import com.dcb.exception.NotFoundException;
import com.dcb.model.*;
import com.dcb.repository.BankAccountRepo;
import com.dcb.repository.TransactionRepo;
import com.dcb.repository.TransferRepo;
import com.dcb.repository.UserRepo;
import com.dcb.util.Util;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    public static final String BANK_NAME = "DIU City Bank";

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final FinancialExecutor executor;
    private final TransactionRepo txnRepo;
    private final TransferRepo transferRepo;
    private final BankAccountRepo accountRepo;
    private final UserRepo userRepo;
    private final BankProperties props;

    public record HistoryFilter(String accountNo, String reference, TransactionType type, TransactionStatus status,
                                LocalDate from, LocalDate to) {
    }

    /** Cash deposit into a customer's account, done by an admin at the branch. */
    public TransactionResponse deposit(String adminId, String accountNo, AmountRequest request, String idempotencyKey) {
        validateAmount(request.getAmount());
        idempotencyService.validateKey(idempotencyKey);
        return executor.run(() -> {
            BankAccountEntity account = lockCustomerAccount(accountNo);
            var prior = idempotencyService.findResult(adminId, idempotencyKey, "DEPOSIT");
            if (prior.isPresent()) return replay(prior.get());

            String reference = Util.generateReference("DEP");
            TransactionStatusEntity entry = ledgerService.credit(account, LedgerService.Posting.builder()
                    .type(TransactionType.DEPOSIT)
                    .amount(request.getAmount())
                    .reference(reference)
                    .description(describe(request.getDescription(), "Cash deposit at branch"))
                    .toBankId(account.getCustomerId())
                    .channel("BRANCH")
                    .build());
            idempotencyService.remember(adminId, idempotencyKey, "DEPOSIT", entry.getTransactionId());
            log.info("Admin {} deposited {} into {}", adminId, request.getAmount(), Util.maskAccount(accountNo));
            return LedgerService.toResponse(entry);
        });
    }

    /** Cash withdrawal from a customer's account, done by an admin at the branch. */
    public TransactionResponse withdraw(String adminId, String accountNo, AmountRequest request, String idempotencyKey) {
        validateAmount(request.getAmount());
        idempotencyService.validateKey(idempotencyKey);
        return executor.run(() -> {
            BankAccountEntity account = lockCustomerAccount(accountNo);
            var prior = idempotencyService.findResult(adminId, idempotencyKey, "WITHDRAWAL");
            if (prior.isPresent()) return replay(prior.get());

            String reference = Util.generateReference("WDL");
            TransactionStatusEntity entry = ledgerService.debit(account, LedgerService.Posting.builder()
                    .type(TransactionType.WITHDRAWAL)
                    .amount(request.getAmount())
                    .reference(reference)
                    .description(describe(request.getDescription(), "Cash withdrawal at branch"))
                    .fromBankId(account.getCustomerId())
                    .channel("BRANCH")
                    .build());
            idempotencyService.remember(adminId, idempotencyKey, "WITHDRAWAL", entry.getTransactionId());
            log.info("Admin {} withdrew {} from {}", adminId, request.getAmount(), Util.maskAccount(accountNo));
            return LedgerService.toResponse(entry);
        });
    }

    /** Owner's bank ID for an account, used to address confirmation mails. */
    public String ownerOf(String accountNo) {
        return accountRepo.findById(accountNo).map(BankAccountEntity::getCustomerId).orElse(null);
    }

    private BankAccountEntity lockCustomerAccount(String accountNo) {
        BankAccountEntity account = accountService.lockAccount(accountNo);
        UserEntity holder = userRepo.findById(account.getCustomerId()).orElse(null);
        if (holder == null || !Boolean.TRUE.equals(holder.getIsActive())) {
            throw new BankException("The account holder's profile is deactivated.");
        }
        accountService.assertActive(account, "This account");
        return account;
    }

    public BeneficiaryResponse lookupBeneficiary(String bankId, String accountNo) {
        BankAccountEntity own = accountService.getAccount(bankId);
        if (own.getAccountNo().equals(accountNo)) {
            throw new BankException("You can't transfer money to your own account.");
        }
        BankAccountEntity beneficiary = accountRepo.findById(accountNo)
                .orElseThrow(() -> new NotFoundException("Beneficiary account not found."));
        UserEntity holder = userRepo.findById(beneficiary.getCustomerId()).orElse(null);
        return BeneficiaryResponse.builder()
                .accountNo(beneficiary.getAccountNo())
                .holderName(holder == null ? "Account holder" : Util.fullName(holder))
                .bankName(BANK_NAME)
                .status(isReceivable(beneficiary, holder) ? "ACTIVE" : "INACTIVE")
                .build();
    }

    public TransferResponse transfer(String bankId, TransferRequest request, String idempotencyKey) {
        validateAmount(request.getAmount());
        idempotencyService.validateKey(idempotencyKey);
        String senderNo = accountService.getAccount(bankId).getAccountNo();
        String receiverNo = request.getBeneficiaryAccountNo();
        if (senderNo.equals(receiverNo)) {
            throw new BankException("Sender and receiver account can't be the same.");
        }
        if (!accountRepo.existsById(receiverNo)) {
            throw new NotFoundException("Beneficiary account not found.");
        }

        return executor.run(() -> {
            // lock both accounts in account-number order so two opposite transfers can't deadlock
            BankAccountEntity sender;
            BankAccountEntity receiver;
            if (senderNo.compareTo(receiverNo) < 0) {
                sender = accountService.lockOperableAccount(bankId);
                receiver = accountService.lockAccount(receiverNo);
            } else {
                receiver = accountService.lockAccount(receiverNo);
                sender = accountService.lockOperableAccount(bankId);
            }

            var prior = idempotencyService.findResult(bankId, idempotencyKey, "TRANSFER");
            if (prior.isPresent()) {
                TransferResponse replay = toTransferResponse(transferRepo.findByReference(prior.get()).orElseThrow());
                replay.setReplayed(true);
                return replay;
            }

            UserEntity receiverUser = userRepo.findById(receiver.getCustomerId()).orElse(null);
            if (!isReceivable(receiver, receiverUser)) {
                throw new BankException("Beneficiary account is not active, transfer can't be processed.");
            }

            String reference = Util.generateReference("TRF");
            String remarks = Util.blankToNull(request.getRemarks());
            String receiverBankId = receiver.getCustomerId();

            TransactionStatusEntity debit = ledgerService.debit(sender, LedgerService.Posting.builder()
                    .type(TransactionType.TRANSFER)
                    .amount(request.getAmount())
                    .reference(reference)
                    .description("Transfer to " + Util.maskAccount(receiverNo) + (remarks == null ? "" : " - " + remarks))
                    .counterpartyAccount(receiverNo)
                    .fromBankId(bankId)
                    .toBankId(receiverBankId)
                    .channel("ONLINE_SIM")
                    .build());
            TransactionStatusEntity credit = ledgerService.credit(receiver, LedgerService.Posting.builder()
                    .type(TransactionType.TRANSFER)
                    .amount(request.getAmount())
                    .reference(reference)
                    .description("Transfer from " + Util.maskAccount(senderNo) + (remarks == null ? "" : " - " + remarks))
                    .counterpartyAccount(senderNo)
                    .fromBankId(bankId)
                    .toBankId(receiverBankId)
                    .channel("ONLINE_SIM")
                    .build());

            TransferEntity transfer = transferRepo.save(TransferEntity.builder()
                    .reference(reference)
                    .senderAccount(senderNo)
                    .receiverAccount(receiverNo)
                    .senderBankId(bankId)
                    .receiverBankId(receiverBankId)
                    .amount(request.getAmount())
                    .remarks(remarks)
                    .status(TransactionStatus.SUCCESS)
                    .debitTransactionId(debit.getTransactionId())
                    .creditTransactionId(credit.getTransactionId())
                    .createdAt(LocalDateTime.now())
                    .build());
            idempotencyService.remember(bankId, idempotencyKey, "TRANSFER", reference);
            log.info("{} transfer {} of {} to {} completed", bankId, reference, request.getAmount(), Util.maskAccount(receiverNo));
            return toTransferResponse(transfer);
        });
    }

    public PageResponse<TransactionResponse> history(String bankId, HistoryFilter filter, int page, int size) {
        String accountNo = accountService.getAccount(bankId).getAccountNo();
        HistoryFilter own = new HistoryFilter(accountNo, filter.reference(), filter.type(), filter.status(), filter.from(), filter.to());
        return search(own, page, size);
    }

    /** Used by both the customer history (account forced to their own) and the admin view. */
    public PageResponse<TransactionResponse> search(HistoryFilter filter, int page, int size) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BankException("'from' date must be on or before 'to' date.");
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(txnRepo.findAll(toSpec(filter), pageable), LedgerService::toResponse);
    }

    public TransactionResponse getOwnTransaction(String bankId, String transactionId) {
        return LedgerService.toResponse(getOwnEntity(bankId, transactionId));
    }

    public TransactionStatusEntity getOwnEntity(String bankId, String transactionId) {
        String accountNo = accountService.getAccount(bankId).getAccountNo();
        return txnRepo.findByTransactionIdAndAccountNo(transactionId, accountNo)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
    }

    public List<TransactionResponse> miniStatement(String bankId, int count) {
        int limit = Math.min(Math.max(count, 1), props.getTxn().getMiniStatementMax());
        String accountNo = accountService.getAccount(bankId).getAccountNo();
        return txnRepo.findByAccountNoOrderByCreatedAtDesc(accountNo, PageRequest.of(0, limit))
                .map(LedgerService::toResponse).getContent();
    }

    public List<TransactionStatusEntity> statementEntries(String accountNo, LocalDate from, LocalDate to) {
        return txnRepo.findByAccountNoAndCreatedAtBetweenOrderByCreatedAtAsc(accountNo, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private Specification<TransactionStatusEntity> toSpec(HistoryFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.accountNo() != null) predicates.add(cb.equal(root.get("accountNo"), f.accountNo()));
            if (Util.blankToNull(f.reference()) != null) {
                String like = "%" + f.reference().trim().toUpperCase() + "%";
                predicates.add(cb.or(cb.like(cb.upper(root.get("reference")), like),
                        cb.like(cb.upper(root.get("transactionId")), like)));
            }
            if (f.type() != null) predicates.add(cb.equal(root.get("type"), f.type()));
            if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay()));
            if (f.to() != null) predicates.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TransactionResponse replay(String transactionId) {
        TransactionResponse response = LedgerService.toResponse(txnRepo.findById(transactionId).orElseThrow());
        response.setReplayed(true);
        return response;
    }

    private TransferResponse toTransferResponse(TransferEntity t) {
        UserEntity receiver = userRepo.findById(t.getReceiverBankId()).orElse(null);
        BigDecimal balanceAfter = txnRepo.findById(t.getDebitTransactionId())
                .map(TransactionStatusEntity::getBalanceAfter).orElse(null);
        return TransferResponse.builder()
                .reference(t.getReference())
                .status(t.getStatus().name())
                .amount(t.getAmount())
                .fromAccount(t.getSenderAccount())
                .toAccount(Util.maskAccount(t.getReceiverAccount()))
                .beneficiaryName(receiver == null ? "Account holder" : Util.fullName(receiver))
                .remarks(t.getRemarks())
                .debitTransactionId(t.getDebitTransactionId())
                .balanceAfter(balanceAfter)
                .createdAt(t.getCreatedAt())
                .simulated(true)
                .build();
    }

    private boolean isReceivable(BankAccountEntity account, UserEntity holder) {
        return account.effectiveStatus() == com.dcb.enums.AccountStatus.ACTIVE
                && holder != null && Boolean.TRUE.equals(holder.getIsActive());
    }

    public void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BankException("Amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new BankException("Amount can have at most 2 decimal places.");
        }
        if (amount.compareTo(props.getTxn().getMinAmount()) < 0) {
            throw new BankException("Minimum amount is " + props.getTxn().getMinAmount().toPlainString() + ".");
        }
        if (amount.compareTo(props.getTxn().getMaxAmount()) > 0) {
            throw new BankException("Maximum amount per transaction is " + props.getTxn().getMaxAmount().toPlainString() + ".");
        }
    }

    private static String describe(String given, String fallback) {
        String value = Util.blankToNull(given);
        return value == null ? fallback : value;
    }
}
