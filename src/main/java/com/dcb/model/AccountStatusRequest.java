package com.dcb.model;

import com.dcb.enums.AccountStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusRequest {
    @NotNull(message = "Status is mandatory (ACTIVE, FROZEN or CLOSED).")
    private AccountStatus status;

    @Size(max = 200, message = "Reason can be at most 200 characters.")
    private String reason;
}
