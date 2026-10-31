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
public class CardLimitResponse {
    private Long cardId;
    private String cardType;
    private String limitType;
    private BigDecimal cardLimit;
    private BigDecimal used;
    private BigDecimal available;
}
