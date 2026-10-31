package com.dcb.service;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.UserEntity;
import com.dcb.enums.AccountStatus;
import com.dcb.exception.BankException;
import com.dcb.exception.NotFoundException;
import com.dcb.model.AccountSummaryResponse;
import com.dcb.repository.BankAccountRepo;
import com.dcb.repository.TransactionRepo;
import com.dcb.repository.UserRepo;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Looks up and locks the authenticated customer's account and enforces account/user status rules. */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final BankAccountRepo accountRepo;
    private final UserRepo userRepo;
    private final TransactionRepo txnRepo;

    public UserEntity getUser(String bankId) {
        return userRepo.findById(bankId).orElseThrow(() -> new NotFoundException("User not found."));
    }

    public BankAccountEntity getAccount(String bankId) {
        return accountRepo.findByCustomerId(bankId).orElseThrow(() -> {
            log.info("{} has no bank account yet.", bankId);
            return new BankException("No bank account found. Your account opening request may still be pending approval.");
        });
    }

    /** Locks the customer's own account (SELECT ... FOR UPDATE) after checking the customer is allowed to transact. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BankAccountEntity lockOperableAccount(String bankId) {
        assertUserActive(getUser(bankId));
        BankAccountEntity account = accountRepo.lockByCustomerId(bankId).orElseThrow(() ->
                new BankException("No bank account found. Your account opening request may still be pending approval."));
        assertActive(account, "Your account");
        return account;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BankAccountEntity lockAccount(String accountNo) {
        return accountRepo.lockByAccountNo(accountNo).orElseThrow(() -> new NotFoundException("Account not found."));
    }

    public void assertUserActive(UserEntity user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BankException("Your profile is deactivated, please contact the bank.");
        }
    }

    public void assertActive(BankAccountEntity account, String who) {
        AccountStatus status = account.effectiveStatus();
        if (status == AccountStatus.FROZEN) {
            throw new BankException(who + " is frozen, transactions are not allowed.");
        }
        if (status == AccountStatus.CLOSED) {
            throw new BankException(who + " is closed.");
        }
    }

    public AccountSummaryResponse summary(String bankId) {
        UserEntity user = getUser(bankId);
        BankAccountEntity account = getAccount(bankId);
        return AccountSummaryResponse.builder()
                .bankId(bankId)
                .accountNo(account.getAccountNo())
                .holderName(Util.fullName(user))
                .accountType(account.getAccountType() == null ? "SAVINGS" : account.getAccountType())
                .status(account.effectiveStatus().name())
                .balance(account.getAccountBalance())
                .availableBalance(account.effectiveStatus() == AccountStatus.ACTIVE ? account.getAccountBalance() : java.math.BigDecimal.ZERO)
                .currency("INR")
                .openedAt(account.getCreatedAt() != null ? account.getCreatedAt()
                        : user.getCreatedDate() != null ? user.getCreatedDate().atStartOfDay() : null)
                .lastTransactionAt(txnRepo.findFirstByAccountNoOrderByCreatedAtDesc(account.getAccountNo())
                        .map(t -> t.getCreatedAt()).orElse(null))
                .build();
    }
}
