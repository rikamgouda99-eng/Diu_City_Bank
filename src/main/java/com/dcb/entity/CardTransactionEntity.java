package com.dcb.entity;

import com.dcb.enums.CardTransactionType;
import com.dcb.enums.CardType;
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
@Table(name = "card_transaction", indexes = @Index(name = "idx_card_txn_card", columnList = "card_id, createdAt"))
public class CardTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_card_txn_card"))
    @ToString.Exclude
    private CardEntity card;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardTransactionType type;

    @Column(nullable = false)
    private String merchantName;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    /** Ledger entry for debit card spends and credit card repayments. */
    private String ledgerTransactionId;

    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
