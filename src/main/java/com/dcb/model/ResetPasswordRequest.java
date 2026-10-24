package com.dcb.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResetPasswordRequest {
    @NotNull(message = "Mail ID is mandatory!")
    private String mailId;
    @NotNull(message = "OTP is mandatory!")
    private String otp;
    @NotNull(message = "New Password is Mandatory!")
    private String password;
}
