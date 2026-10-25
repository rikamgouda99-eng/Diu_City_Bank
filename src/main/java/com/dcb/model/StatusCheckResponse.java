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
public class StatusCheckResponse {
    private String bankId;
    private String mailId;
    private String name;
    private String accountCreationDate;
    private String lastUpdateDate;
    private String status;
    private Boolean isActive;
    private Boolean isApproved;
    private Boolean isVerified;
}
