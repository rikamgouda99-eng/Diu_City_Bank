package com.dcb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;


@Table
@Entity
@Data
public class CardEntity
{
    @Id
    private String cardNumber;
    @Column(name = "cvv", nullable = false)
    private String cvv;
    private LocalDate validUpto;
    @Column(name = "pin_hash", nullable = false)
    private String pinHash;
    @Column(name = "card_limit", precision = 15, scale = 2)
    private Double cardLimit;
}
