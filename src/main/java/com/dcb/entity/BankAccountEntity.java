package com.dcb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "bank_account")
public class BankAccountEntity
{
    @Id
    @Column(name="account_no")
    private String accountNo;
    private String customerId;// it takes reference from UserEntity
    private Double accountBalance;
    private String fixedDepositId;// it takes reference form FD entity
}
