package com.dcb.model;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {
    @NotBlank(message = "First name is mandatory.")
    @Size(max = 50, message = "First name can be at most 50 characters.")
    @Pattern(regexp = "[\\p{L} .'-]+", message = "First name contains invalid characters.")
    private String firstName;

    @Size(max = 50, message = "Middle name can be at most 50 characters.")
    @Pattern(regexp = "[\\p{L} .'-]*", message = "Middle name contains invalid characters.")
    private String middleName;

    @NotBlank(message = "Last name is mandatory.")
    @Size(max = 50, message = "Last name can be at most 50 characters.")
    @Pattern(regexp = "[\\p{L} .'-]+", message = "Last name contains invalid characters.")
    private String lastName;

    @NotBlank(message = "Mobile number is mandatory.")
    @Pattern(regexp = "\\+?\\d{10,13}", message = "Mobile number must be 10-13 digits.")
    private String phoneNumber;

    @NotBlank(message = "Address is mandatory.")
    @Size(max = 250, message = "Address can be at most 250 characters.")
    private String addressDetails;

    @Size(max = 30, message = "Gender can be at most 30 characters.")
    private String gender;

    @NotBlank(message = "Marital status is mandatory.")
    @Size(max = 30, message = "Marital status can be at most 30 characters.")
    private String maritalStatus;
}
