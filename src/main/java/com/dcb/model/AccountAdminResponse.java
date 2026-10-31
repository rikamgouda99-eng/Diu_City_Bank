package com.dcb.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class AccountAdminResponse {
    private String accountNo;
    private String bankId;
    private String holderName;
    private String accountType;
    private BigDecimal balance;
    private String status;
    private String statusReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
