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
public class KycResponse {
    private Long id;
    private String bankId;
    private String customerName;
    private String documentType;
    private String documentNumber;
    private String fileName;
    private String contentType;
    private String status;
    private String adminComment;
    private String reviewedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
