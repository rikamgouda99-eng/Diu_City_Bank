package com.dcb.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FdCreateRequest {
    @NotNull(message = "Amount is mandatory.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    @NotNull(message = "Tenure is mandatory.")
    @Min(value = 1, message = "Tenure must be at least 1 month.")
    private Integer tenureMonths;

    /** Optional. Defaults to the configured rate for the tenure. */
    @DecimalMin(value = "0.01", message = "Interest rate must be positive.")
    @Digits(integer = 3, fraction = 2, message = "Interest rate can have at most 2 decimal places.")
    private BigDecimal interestRate;
}
