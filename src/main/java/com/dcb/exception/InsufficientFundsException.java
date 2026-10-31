package com.dcb.exception;

import com.dcb.enums.TransactionType;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Raised when a debit would take a balance, credit line or card limit below zero.
 * Carries enough detail for a FAILED record to be written after the rollback.
 */
@Getter
public class InsufficientFundsException extends BankException {
    private final String accountNo;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String description;
    /** Set for card operations: the failure is recorded as a card transaction instead of a ledger entry. */
    private final Long cardId;

    public InsufficientFundsException(String message, String accountNo, TransactionType type,
                                      BigDecimal amount, String description) {
        this(message, accountNo, type, amount, description, null);
    }

    public InsufficientFundsException(String message, String accountNo, TransactionType type,
                                      BigDecimal amount, String description, Long cardId) {
        super(message);
        this.accountNo = accountNo;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.cardId = cardId;
    }
}
