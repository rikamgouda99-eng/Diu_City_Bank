package com.dcb.model;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmiPaymentRequest {
    /** Optional guard: the EMI number the customer intends to pay. */
    @Min(value = 1, message = "EMI number must be positive.")
    private Integer emiNumber;
}
