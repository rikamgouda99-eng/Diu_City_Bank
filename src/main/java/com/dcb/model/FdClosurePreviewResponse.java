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
public class FdClosurePreviewResponse {
    private String fdId;
    private String closureType;
    private BigDecimal principal;
    private BigDecimal contractedRate;
    private BigDecimal penaltyRate;
    private BigDecimal applicableRate;
    private Long daysHeld;
    private BigDecimal interestEarned;
    private BigDecimal payoutAmount;
    private LocalDate maturityDate;
    private BigDecimal maturityAmountIfHeld;
    private String note;
}
