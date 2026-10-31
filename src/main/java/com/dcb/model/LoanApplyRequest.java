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
public class LoanApplyRequest {
    @NotNull(message = "Loan type is mandatory.")
    private LoanType loanType;

    @NotNull(message = "Amount is mandatory.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    @NotNull(message = "Tenure is mandatory.")
    @Min(value = 1, message = "Tenure must be at least 1 month.")
    private Integer tenureMonths;

    @NotBlank(message = "Employment type is mandatory.")
    @Pattern(regexp = "SALARIED|SELF_EMPLOYED|STUDENT|RETIRED|OTHER", message = "Employment type must be SALARIED, SELF_EMPLOYED, STUDENT, RETIRED or OTHER.")
    private String employmentType;

    @Size(max = 100, message = "Employer name can be at most 100 characters.")
    private String employerName;

    @NotNull(message = "Monthly income is mandatory.")
    @DecimalMin(value = "0.00", message = "Monthly income can't be negative.")
    @Digits(integer = 13, fraction = 2, message = "Monthly income can have at most 2 decimal places.")
    private BigDecimal monthlyIncome;

    @Size(max = 200, message = "Purpose can be at most 200 characters.")
    private String purpose;
}
