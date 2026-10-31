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
public class LoanResponse {
    private String loanId;
    private String bankId;
    private String customerName;
    private String accountNo;
    private String loanType;
    private String status;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal monthlyEmi;
    private BigDecimal totalInterest;
    private BigDecimal totalRepayment;
    private BigDecimal outstandingPrincipal;
    private LocalDate nextEmiDueDate;
    private BigDecimal nextEmiAmount;
    private Integer emisPaid;
    private Integer emisPending;
    private String employmentType;
    private String employerName;
    private BigDecimal monthlyIncome;
    private String purpose;
    private String adminComment;
    private String disbursalTransactionId;
    private LocalDateTime appliedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime closedAt;
    private Boolean simulated;
}
