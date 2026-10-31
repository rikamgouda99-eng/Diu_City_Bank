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
public class TransferResponse {
    private String reference;
    private String status;
    private BigDecimal amount;
    private String fromAccount;
    private String toAccount;
    private String beneficiaryName;
    private String remarks;
    private String debitTransactionId;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
    private Boolean replayed;
    private Boolean simulated;
}
