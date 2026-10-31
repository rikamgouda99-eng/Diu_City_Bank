package com.dcb.entity;

import com.dcb.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "bank_account", indexes = @Index(name = "idx_account_customer", columnList = "customerId"))
@AllArgsConstructor
@NoArgsConstructor
public class BankAccountEntity
{
    @Id
    @Column(name="account_no")
    private String accountNo;
    private String customerId;// it takes reference from UserEntity
    @Column(precision = 19, scale = 2)
    private BigDecimal accountBalance;
    private String fixedDepositId;// it takes reference form FD entity

    @Column(length = 20)
    private String accountType;

    // null on accounts created before account status existed, treated as ACTIVE
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AccountStatus status;

    private String statusReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AccountStatus effectiveStatus() {
        return status == null ? AccountStatus.ACTIVE : status;
    }
}
