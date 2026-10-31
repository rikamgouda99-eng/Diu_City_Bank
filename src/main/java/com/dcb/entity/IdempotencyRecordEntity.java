package com.dcb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Remembers the result of every financial request by (bank ID, Idempotency-Key),
 * so a retried request returns the original result instead of moving money twice.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "idempotency_record")
public class IdempotencyRecordEntity {

    /** bankId + ":" + client key */
    @Id
    @Column(length = 160)
    private String id;

    @Column(nullable = false, length = 40)
    private String operation;

    /** ID of the record the original request produced (transaction, FD, EMI...). */
    @Column(nullable = false, length = 60)
    private String resultId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
