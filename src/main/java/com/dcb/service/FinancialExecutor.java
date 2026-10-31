package com.dcb.service;

import com.dcb.exception.InsufficientFundsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs a money movement in one database transaction. If it is rejected for lack of funds
 * the transaction rolls back and a FAILED record is written separately, so the attempt
 * still shows up in the customer's history.
 */
@Component
@Slf4j
public class FinancialExecutor {

    private final TransactionTemplate tx;
    private final LedgerService ledgerService;

    public FinancialExecutor(PlatformTransactionManager transactionManager, LedgerService ledgerService) {
        this.tx = new TransactionTemplate(transactionManager);
        this.ledgerService = ledgerService;
    }

    public <T> T run(Supplier<T> work) {
        try {
            return tx.execute(status -> work.get());
        } catch (InsufficientFundsException e) {
            try {
                tx.executeWithoutResult(status -> ledgerService.recordFailed(e));
            } catch (RuntimeException recordError) {
                log.warn("Could not record failed transaction: {}", recordError.getMessage());
            }
            throw e;
        }
    }
}
