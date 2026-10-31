package com.dcb.entity;

import com.dcb.enums.BillType;
import com.dcb.enums.PaymentMode;
import com.dcb.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bill_payment", indexes = @Index(name = "idx_bill_bank", columnList = "bank_id"))
public class BillPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long billId;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(name = "bank_id", nullable = false)
    private String bankId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_bill_user"))
    @ToString.Exclude
    private UserEntity user;

    @Column(nullable = false)
    private String accountNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillType billType;

    private String billerName;

    @Column(nullable = false, length = 40)
    private String consumerNumber;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMode paymentMode;

    /** Ledger entry, set for account-funded modes (UPI, bank draft, debit card). */
    @Column(name = "transaction_id", length = 40)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_bill_txn"))
    @ToString.Exclude
    private TransactionStatusEntity transaction;

    /** Card transaction, set for card modes. */
    private Long cardTransactionId;

    private Long cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false)
    private LocalDateTime paymentDate;
}
