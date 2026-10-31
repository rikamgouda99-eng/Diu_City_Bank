package com.dcb.model;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosureRequest {
    @NotBlank(message = "Password is mandatory to close the account.")
    private String password;

    @NotBlank(message = "Reason is mandatory.")
    @Size(max = 200, message = "Reason can be at most 200 characters.")
    private String reason;
}
