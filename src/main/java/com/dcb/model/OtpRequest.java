package com.dcb.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OtpRequest {

    @NotNull(message = "Mail ID is mandatory.")
    @Email(message = "Please enter a valid mail ID")
    private String mailId;

    @NotBlank(message = "OPT is mandatory")
    private String otp;
}
