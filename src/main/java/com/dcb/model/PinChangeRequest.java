package com.dcb.model;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PinChangeRequest {
    /** Required when a PIN is already set. */
    @Pattern(regexp = "\\d{4}", message = "Current PIN must be 4 digits.")
    private String oldPin;

    @NotBlank(message = "New PIN is mandatory.")
    @Pattern(regexp = "\\d{4}", message = "New PIN must be 4 digits.")
    private String newPin;
}
