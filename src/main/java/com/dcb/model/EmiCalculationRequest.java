package com.dcb.model;

import com.dcb.enums.LoanType;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmiCalculationRequest {
    @NotNull(message = "Loan type is mandatory.")
    private LoanType loanType;

    @NotNull(message = "Amount is mandatory.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    /** Optional. Defaults to the configured rate for the loan type. */
    @DecimalMin(value = "0.00", message = "Interest rate can't be negative.")
    @Digits(integer = 3, fraction = 2, message = "Interest rate can have at most 2 decimal places.")
    private BigDecimal annualInterestRate;

    @NotNull(message = "Tenure is mandatory.")
    @Min(value = 1, message = "Tenure must be at least 1 month.")
    private Integer tenureMonths;
}
