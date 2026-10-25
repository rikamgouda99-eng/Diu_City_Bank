package com.dcb.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private String bankID;
    private String accountNumber;
    private String accountBalance;
    private String fixedAccountNumber;
    private String loanAccountNumber;
    private String mailId;
    private String name;
    private String dob;
    private String gender;
    private String address;
    private String accountOpeningDate;
    private String accountUpdateDate;
    private String maritalStatus;
    private String phoneNumber;
}