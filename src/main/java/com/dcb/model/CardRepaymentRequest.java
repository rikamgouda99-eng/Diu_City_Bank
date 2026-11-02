package com.dcb.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardRepaymentRequest {
    @NotNull(message = "Amount is mandatory.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    @NotBlank(message = "Card PIN is mandatory.")
    @Pattern(regexp = "\\d{4}", message = "Card PIN must be 4 digits.")
    private String pin;

    @NotBlank(message = "OTP is mandatory.")
    @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits.")
    private String otp;
}
