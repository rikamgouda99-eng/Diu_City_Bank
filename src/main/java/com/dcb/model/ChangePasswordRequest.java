package com.dcb.model;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    @NotBlank(message = "Current password is mandatory.")
    private String oldPassword;

    @NotBlank(message = "New password is mandatory.")
    @Size(min = 8, max = 64, message = "New password must be 8-64 characters.")
    private String newPassword;
}
