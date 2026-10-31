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
public class AdminDashboardResponse {
    private Long totalCustomers;
    private Long pendingApprovals;
    private Long totalAccounts;
    private BigDecimal totalDeposits;
    private Long transactionsToday;
    private Long failedTransactions;
    private Long pendingFds;
    private Long activeFds;
    private Long pendingCardApplications;
    private Long activeCards;
    private Long pendingLoans;
    private Long activeLoans;
    private BigDecimal loanOutstanding;
    private Long pendingKyc;
    private Long openRequests;
}
