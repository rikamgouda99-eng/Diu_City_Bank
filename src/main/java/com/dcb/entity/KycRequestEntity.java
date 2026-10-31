package com.dcb.entity;

import com.dcb.enums.KycDocumentType;
import com.dcb.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "kyc_request", indexes = @Index(name = "idx_kyc_bank", columnList = "bank_id"))
public class KycRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", nullable = false)
    private String bankId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_kyc_user"))
    @ToString.Exclude
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycDocumentType documentType;

    @Column(nullable = false, length = 30)
    private String documentNumber;

    private String fileName;

    @Column(length = 50)
    private String contentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 20_000_000)
    @ToString.Exclude
    private byte[] documentData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private KycStatus status;

    private String adminComment;

    private String reviewedBy;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;
}
