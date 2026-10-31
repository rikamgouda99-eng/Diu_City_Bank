package com.dcb.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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
public class FdQuoteResponse {
    private BigDecimal principal;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private Integer compoundingPerYear;
    private BigDecimal totalInterest;
    private BigDecimal maturityAmount;
    private LocalDate depositDate;
    private LocalDate maturityDate;
    private String formula;
}
