package com.dcb.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardApprovalRequest {
    /** Debit: daily limit. Credit: credit limit (mandatory for credit cards). */
    @DecimalMin(value = "1000.00", message = "Card limit must be at least 1000.")
    @Digits(integer = 13, fraction = 2, message = "Card limit can have at most 2 decimal places.")
    private BigDecimal cardLimit;

    @Size(max = 250, message = "Comment can be at most 250 characters.")
    private String comment;
}
