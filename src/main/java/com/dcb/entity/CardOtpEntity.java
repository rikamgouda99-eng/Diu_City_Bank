package com.dcb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One-time password for a credit card payment. One live OTP per card; it is deleted once used. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "card_otp")
public class CardOtpEntity {

    @Id
    private Long cardId;

    @Column(nullable = false)
    private String otpHash;

    @Column(nullable = false)
    private LocalDateTime expiry;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
