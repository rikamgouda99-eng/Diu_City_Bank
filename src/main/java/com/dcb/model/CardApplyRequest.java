package com.dcb.model;

import com.dcb.enums.CardScheme;
import com.dcb.enums.CardType;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardApplyRequest {
    @NotNull(message = "Card type is mandatory (DEBIT or CREDIT).")
    private CardType cardType;

    @NotNull(message = "Card scheme is mandatory (VISA, MASTERCARD or RUPAY).")
    private CardScheme cardScheme;

    /** Credit cards only: limit the customer asks for; the admin decides. */
    @DecimalMin(value = "1000.00", message = "Requested limit must be at least 1000.")
    @Digits(integer = 13, fraction = 2, message = "Requested limit can have at most 2 decimal places.")
    private BigDecimal requestedLimit;
}
