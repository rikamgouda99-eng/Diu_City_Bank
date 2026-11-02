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
public class CardBillingResponse {
    private BigDecimal totalOutstanding;
    private BigDecimal minimumDue;
    private LocalDate statementDate;
    private LocalDate paymentDueDate;
    private LocalDate nextStatementDate;
    private Long daysToDue;
}
