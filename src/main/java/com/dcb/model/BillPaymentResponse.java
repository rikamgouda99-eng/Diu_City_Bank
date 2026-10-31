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
public class BillPaymentResponse {
    private Long billId;
    private String reference;
    private String billType;
    private String billerName;
    private String consumerNumber;
    private BigDecimal amount;
    private String paymentMode;
    private String transactionId;
    private Long cardTransactionId;
    private String cardMasked;
    private String status;
    private LocalDateTime paymentDate;
    private String bankId;
    private Boolean replayed;
    private Boolean simulated;
}
