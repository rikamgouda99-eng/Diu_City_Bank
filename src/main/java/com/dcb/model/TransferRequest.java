package com.dcb.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    @NotBlank(message = "Beneficiary account number is mandatory.")
    @Pattern(regexp = "\\d{8,20}", message = "Beneficiary account number must be 8-20 digits.")
    private String beneficiaryAccountNo;

    @NotNull(message = "Amount is mandatory.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    @Size(max = 200, message = "Remarks can be at most 200 characters.")
    private String remarks;
}
