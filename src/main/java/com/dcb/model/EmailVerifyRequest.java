package com.dcb.model;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerifyRequest {
    @NotBlank(message = "New email is mandatory.")
    @Email(message = "Please enter a valid email.")
    private String newEmail;

    @NotBlank(message = "OTP is mandatory.")
    @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits.")
    private String otp;
}
