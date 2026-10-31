
package com.dcb.entity;

import com.dcb.enums.CardScheme;
import com.dcb.enums.CardStatus;
import com.dcb.enums.CardType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Simulated debit/credit card. The full card number and CVV are never stored:
 * only the masked number, a random token and a BCrypt hash of the CVV and PIN.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "card_entity", indexes = @Index(name = "idx_card_bank", columnList = "bank_id"))
public class CardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cardId;

    @Column(unique = true, length = 40)
    private String cardToken;

    @Column(length = 25)
    private String maskedNumber;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private CardType cardType;

    @Column(nullable = false, length = 15)
    @Enumerated(EnumType.STRING)
    private CardScheme cardScheme;

    @Column(nullable = false, length = 15)
    @Enumerated(EnumType.STRING)
    private CardStatus status;

    @Column(name = "bank_id", nullable = false)
    private String bankId;

    @Column(name = "account_no", nullable = false)
    private String accountNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_no", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_card_account"))
    @ToString.Exclude
    private BankAccountEntity account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_card_user"))
    @ToString.Exclude
    private UserEntity user;

    private String holderName;

    private LocalDate validUpto;

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "cvv_hash")
    private String cvvHash;

    /** Debit: daily spend limit. Credit: total credit limit. */
    @Column(name = "card_limit", precision = 19, scale = 2)
    private BigDecimal cardLimit;

    @Column(precision = 19, scale = 2)
    private BigDecimal requestedLimit;

    @Column(precision = 19, scale = 2)
    private BigDecimal creditUsed;

    /** CUSTOMER or ADMIN; a customer can't unblock a card the bank blocked. */
    @Column(length = 10)
    private String blockedBy;

    private Long replacementOfCardId;

    private String adminComment;

    private LocalDate issuedDate;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime updatedAt;
}
