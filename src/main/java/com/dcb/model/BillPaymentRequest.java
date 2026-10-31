package com.dcb.model;

import com.dcb.enums.BillType;
import com.dcb.enums.PaymentMode;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentRequest {
    @NotNull(message = "Bill type is mandatory.")
    private BillType billType;

    @Size(max = 100, message = "Biller name can be at most 100 characters.")
    private String billerName;

    @NotBlank(message = "Consumer number is mandatory.")
    @Pattern(regexp = "[A-Za-z0-9-]{4,30}", message = "Consumer number must be 4-30 letters, digits or hyphens.")
    private String consumerNumber;

    @NotNull(message = "Amount is mandatory.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    @NotNull(message = "Payment mode is mandatory.")
    private PaymentMode paymentMode;

    /** Required for DEBIT_CARD and CREDIT_CARD. */
    private Long cardId;

    @Pattern(regexp = "\\d{4}", message = "Card PIN must be 4 digits.")
    private String cardPin;
}
