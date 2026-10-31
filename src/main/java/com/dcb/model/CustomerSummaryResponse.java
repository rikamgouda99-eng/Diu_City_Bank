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
public class CustomerSummaryResponse {
    private String bankId;
    private String name;
    private String mailId;
    private String phoneNumber;
    private String status;
    private Boolean isActive;
    private Boolean isVerified;
    private Boolean isApproved;
    private String accountNo;
    private BigDecimal balance;
    private String accountStatus;
    private String kycStatus;
    private LocalDate createdDate;
}
