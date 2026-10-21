package com.dcb.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginRequest {

    @NotNull(message = "Mail ID is mandatory, please provide a valid mailId")
    private String mailId;

    @NotNull(message = "first name is mandatory, please provide your first name")
    private  String firstName;
    private  String middleName;

    @NotNull(message = "last name is mandatory, please provide your last name")
    private  String lastName;

    @NotNull(message = "Address is mandatory, please provide your address")
    private  String addressDetails;

    @NotNull(message = "Phone number is mandatory, please provide phone number")
    private String phoneNumber;

    private  String gender;
    @NotNull(message = "DOB is mandatory, please provide your DOB")
    private LocalDate dob;//yyyy-mm-dd

    @NotNull(message = "marital status is mandatory, please provide your marital status")
    private String maritalStatus;//single, married, divorced, prefer not to disclose
    @NotNull(message = "Password is mandatory, please provide your password")
    private  String password;
}
