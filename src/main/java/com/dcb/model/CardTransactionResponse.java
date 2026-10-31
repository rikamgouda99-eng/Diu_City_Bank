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
public class CardTransactionResponse {
    private Long id;
    private String reference;
    private Long cardId;
    private String maskedNumber;
    private String cardType;
    private String type;
    private String merchantName;
    private BigDecimal amount;
    private String status;
    private String ledgerTransactionId;
    private String failureReason;
    private BigDecimal availableAfter;
    private LocalDateTime createdAt;
    private Boolean replayed;
    private Boolean simulated;
}
