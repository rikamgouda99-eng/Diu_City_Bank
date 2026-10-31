package com.dcb.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FdResponse {
    private String fdId;
    private String bankId;
    private String customerName;
    private String accountNo;
    private BigDecimal principal;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private Integer compoundingPerYear;
    private LocalDate depositDate;
    private LocalDate maturityDate;
    private BigDecimal expectedInterest;
    private BigDecimal maturityAmount;
    private String status;
    private String closureType;
    private BigDecimal payoutAmount;
    private BigDecimal penaltyRate;
    private String fundingTransactionId;
    private String payoutTransactionId;
    private String adminComment;
    private LocalDateTime approvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private Long daysToMaturity;
    private Boolean replayed;
    private Boolean simulated;
}
