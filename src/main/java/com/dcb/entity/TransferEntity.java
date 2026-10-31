package com.dcb.entity;

import com.dcb.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Sender/receiver details of an account-to-account transfer. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fund_transfer", indexes = {
        @Index(name = "idx_transfer_sender", columnList = "sender_account"),
        @Index(name = "idx_transfer_receiver", columnList = "receiver_account")
})
public class TransferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(name = "sender_account", nullable = false)
    private String senderAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_transfer_sender"))
    @ToString.Exclude
    private BankAccountEntity sender;

    @Column(name = "receiver_account", nullable = false)
    private String receiverAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_account", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_transfer_receiver"))
    @ToString.Exclude
    private BankAccountEntity receiver;

    @Column(nullable = false)
    private String senderBankId;

    @Column(nullable = false)
    private String receiverBankId;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    private String debitTransactionId;

    private String creditTransactionId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
