package com.dcb.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
@Table(name="transaction_status_table")
public class TransactionStatusEntity
{
    @Id
    private String transactionId;
    private String fromBankId;
    private String toBankId;
    private Double transactionAmount;
    private LocalDate transactionDate;
    private LocalDate updateDate;
    private String status;
    private String rrn;
    private Long stan;
}
