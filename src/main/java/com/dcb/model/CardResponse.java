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
public class CardResponse {
    private Long cardId;
    private String bankId;
    private String maskedNumber;
    private String cardType;
    private String cardScheme;
    private String holderName;
    private String expiry;
    private String linkedAccount;
    private BigDecimal cardLimit;
    private BigDecimal requestedLimit;
    private BigDecimal creditUsed;
    private BigDecimal availableCredit;
    private BigDecimal spentToday;
    private BigDecimal availableToday;
    private String status;
    private String blockedBy;
    private Boolean pinSet;
    private LocalDate issuedDate;
    private LocalDateTime appliedAt;
    private String adminComment;
    private Long replacementOfCardId;
    private CardBillingResponse billing;
    private Boolean simulated;
}
