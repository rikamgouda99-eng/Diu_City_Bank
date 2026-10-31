package com.dcb.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanApprovalRequest {
    /** Optional; defaults to the requested amount. */
    @DecimalMin(value = "1.00", message = "Approved amount must be positive.")
    @Digits(integer = 13, fraction = 2, message = "Approved amount can have at most 2 decimal places.")
    private BigDecimal approvedAmount;

    /** Optional; defaults to the configured rate for the loan type. */
    @DecimalMin(value = "0.00", message = "Interest rate can't be negative.")
    @Digits(integer = 3, fraction = 2, message = "Interest rate can have at most 2 decimal places.")
    private BigDecimal interestRate;

    @Size(max = 250, message = "Comment can be at most 250 characters.")
    private String comment;
}
