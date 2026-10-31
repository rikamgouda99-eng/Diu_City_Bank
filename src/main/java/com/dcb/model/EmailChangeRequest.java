package com.dcb.model;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailChangeRequest {
    @NotBlank(message = "New email is mandatory.")
    @Email(message = "Please enter a valid email.")
    private String newEmail;
}
