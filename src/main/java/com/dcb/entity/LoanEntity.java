package com.dcb.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

@Entity
@Data
@Table(name="loan_details")

public class LoanEntity
{
    @Id
    private String loanId;
    private  String accountNo;// FK
    private Double interestRate;
    private Date duration;

}
