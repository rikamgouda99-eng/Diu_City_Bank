package com.dcb.model;

import java.math.BigDecimal;
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
public class TransactionResponse {
    private String transactionId;
    private String reference;
    private String accountNo;
    private String type;
    private String direction;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String status;
    private String description;
    private String counterpartyAccount;
    private String channel;
    private String rrn;
    private Long stan;
    private LocalDateTime createdAt;
    /** true when this response replays an earlier request with the same Idempotency-Key */
    private Boolean replayed;
    private Boolean simulated;
}
