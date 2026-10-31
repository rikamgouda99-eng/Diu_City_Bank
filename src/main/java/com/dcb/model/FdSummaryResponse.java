package com.dcb.model;

import java.math.BigDecimal;
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
public class FdSummaryResponse {
    private Long totalFds;
    private Long activeFds;
    private Long pendingFds;
    private Long maturedFds;
    private Long closedFds;
    private Long rejectedFds;
    private BigDecimal totalInvested;
    private BigDecimal totalExpectedInterest;
    private BigDecimal totalMaturityAmount;
}
