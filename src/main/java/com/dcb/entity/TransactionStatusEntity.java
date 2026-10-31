
package com.dcb.entity;

import com.dcb.enums.EntryDirection;
import com.dcb.enums.TransactionStatus;
import com.dcb.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Account ledger. One row per account movement: a transfer produces a DEBIT row
 * for the sender and a CREDIT row for the receiver sharing the same reference.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transaction_status_table", indexes = {
        @Index(name = "idx_txn_account_created", columnList = "account_no, createdAt"),
        @Index(name = "idx_txn_reference", columnList = "reference"),
        @Index(name = "idx_txn_status", columnList = "status")
})
public class TransactionStatusEntity {

    @Id
    @Column(length = 40)
    private String transactionId;

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(name = "account_no", nullable = false)
    private String accountNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_no", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_txn_account"))
    @ToString.Exclude
    private BankAccountEntity account;

    private String fromBankId;

    private String toBankId;

    private String counterpartyAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryDirection direction;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal transactionAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 255)
    private String description;

    @Column(length = 30)
    private String channel;

    private String rrn;

    private Long stan;

    private LocalDate transactionDate;

    private LocalDate updateDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
