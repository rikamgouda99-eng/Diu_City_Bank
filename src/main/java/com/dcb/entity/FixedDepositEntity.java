package com.dcb.entity;

import com.dcb.enums.FdClosureType;
import com.dcb.enums.FdStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fixed_deposit", indexes = {
        @Index(name = "idx_fd_bank", columnList = "bank_id"),
        @Index(name = "idx_fd_status_maturity", columnList = "status, maturityDate")
})
public class FixedDepositEntity {

    @Id
    @Column(length = 30)
    private String fdId;

    @Column(name = "account_no", nullable = false)
    private String accountNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_no", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_fd_account"))
    @ToString.Exclude
    private BankAccountEntity account;

    @Column(name = "bank_id", nullable = false)
    private String bankId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_fd_user"))
    @ToString.Exclude
    private UserEntity user;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal principal;

    @Column(precision = 6, scale = 2, nullable = false)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer tenureMonths;

    @Column(nullable = false)
    private Integer compoundingPerYear;

    @Column(nullable = false)
    private LocalDate depositDate;

    @Column(nullable = false)
    private LocalDate maturityDate;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal maturityAmount;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal expectedInterest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private FdStatus status;

    /** Ledger debit that moved the principal out of the savings account. */
    private String fundingTransactionId;

    /** Ledger credit for the payout or refund. Unique so a payout can only happen once. */
    @Column(unique = true)
    private String payoutTransactionId;

    @Column(precision = 19, scale = 2)
    private BigDecimal payoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private FdClosureType closureType;

    @Column(precision = 6, scale = 2)
    private BigDecimal penaltyRate;

    private String adminComment;

    private LocalDateTime approvedAt;

    private LocalDateTime closedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
