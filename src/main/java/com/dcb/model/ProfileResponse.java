package com.dcb.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponse {
    private String bankId;
    private String customerId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private String mailId;
    private String phoneNumber;
    private String gender;
    private LocalDate dob;
    private String maritalStatus;
    private String addressDetails;
    private String role;
    private LocalDate createdDate;
    private LocalDate updatedDate;
    private Boolean isVerified;
    private Boolean isApproved;
    private Boolean isActive;
    private String applicationStatus;
    private String kycStatus;
    private Boolean hasPhoto;
    private String accountNo;
    private String accountType;
    private String accountStatus;
    private BigDecimal accountBalance;
}
