package com.dcb.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApproveUserRequest {
    @NotNull(message = "Bank ID is mandatory!")
    private String bankId;
    @NotNull(message = "action is mandatory, accepted value = [APPROVE, REJECT]")
    private String action;//APPROVE / REJECT
    private String status;
}
