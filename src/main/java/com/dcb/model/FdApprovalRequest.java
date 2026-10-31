package com.dcb.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FdApprovalRequest {
    /** Optional override of the rate the customer was quoted. */
    @DecimalMin(value = "0.01", message = "Interest rate must be positive.")
    @Digits(integer = 3, fraction = 2, message = "Interest rate can have at most 2 decimal places.")
    private BigDecimal interestRate;

    @Size(max = 250, message = "Comment can be at most 250 characters.")
    private String comment;
}
