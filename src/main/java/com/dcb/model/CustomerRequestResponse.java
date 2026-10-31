package com.dcb.model;

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
public class CustomerRequestResponse {
    private Long id;
    private String bankId;
    private String customerName;
    private String requestType;
    private String subject;
    private String description;
    private String status;
    private String adminComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
