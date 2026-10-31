package com.dcb.entity;

import com.dcb.enums.LoanStatus;
import com.dcb.enums.LoanType;
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
@Table(name="loan_details", indexes = @Index(name = "idx_loan_bank", columnList = "bank_id"))
public class LoanEntity
{
    @Id
    @Column(length = 30)
    private String loanId;

    @Column(name = "account_no", nullable = false)
    private  String accountNo;// FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_no", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_loan_account"))
    @ToString.Exclude
    private BankAccountEntity account;

    @Column(name = "bank_id", nullable = false)
    private String bankId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_loan_user"))
    @ToString.Exclude
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private LoanType loanType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private LoanStatus status;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal requestedAmount;

    @Column(nullable = false)
    private Integer tenureMonths;

    @Column(precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Column(precision = 6, scale = 2)
    private BigDecimal interestRate;

    @Column(precision = 19, scale = 2)
    private BigDecimal emiAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalInterest;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalRepayment;

    @Column(precision = 19, scale = 2)
    private BigDecimal outstandingPrincipal;

    @Column(length = 30)
    private String employmentType;

    private String employerName;

    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyIncome;

    private String purpose;

    private String adminComment;

    private String disbursalTransactionId;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime decidedAt;

    private LocalDate firstEmiDate;

    private LocalDateTime closedAt;

    private LocalDateTime updatedAt;
}
