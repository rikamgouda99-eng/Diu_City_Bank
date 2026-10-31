package com.dcb.entity;

import com.dcb.enums.EmiStatus;
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
@Table(name = "emi_schedule", uniqueConstraints =
        @UniqueConstraint(name = "uk_emi_loan_number", columnNames = {"loan_id", "emiNumber"}))
public class EmiScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false, length = 30)
    private String loanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_emi_loan"))
    @ToString.Exclude
    private LoanEntity loan;

    @Column(nullable = false)
    private Integer emiNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal principalComponent;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal interestComponent;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal emiAmount;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal remainingPrincipal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EmiStatus status;

    private LocalDateTime paymentDate;

    private String transactionId;
}
