package com.dcb.controller;

import com.dcb.model.APIResponse;
import com.dcb.model.BillPaymentRequest;
import com.dcb.service.BillService;
import com.dcb.service.NotificationService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;
    private final NotificationService notifier;
    private final Util util;

    @GetMapping("/options")
    public ResponseEntity<APIResponse<?>> options() {
        return ResponseEntity.ok(util.getSuccessResponse(billService.options(), "Bill types and payment modes."));
    }

    @PostMapping("/pay")
    public ResponseEntity<APIResponse<?>> pay(@AuthenticationPrincipal UserDetails user,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @RequestBody @Valid BillPaymentRequest request) {
        var response = billService.pay(user.getUsername(), request, key);
        if (!Boolean.TRUE.equals(response.getReplayed())) {
            boolean recharge = "MOBILE_RECHARGE".equals(response.getBillType());
            notifier.notify(user.getUsername(), recharge ? "Mobile recharge successful" : "Bill payment successful",
                    recharge ? "Your mobile recharge was completed." : "Your " + response.getBillType().replace('_', ' ').toLowerCase() + " bill was paid.",
                    NotificationService.details()
                            .amount("Amount", response.getAmount())
                            .add(recharge ? "Mobile number" : "Consumer number", response.getConsumerNumber())
                            .add(recharge ? "Operator" : "Biller", response.getBillerName())
                            .add("Paid by", response.getPaymentMode().replace('_', ' ').toLowerCase() + (response.getCardMasked() == null ? "" : " " + response.getCardMasked()))
                            .add("Reference", response.getReference()));
        }
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Bill paid successfully (simulated)."));
    }

    @GetMapping("/history")
    public ResponseEntity<APIResponse<?>> history(@AuthenticationPrincipal UserDetails user,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(billService.history(user.getUsername(), page, size), "Bill payment history fetched."));
    }
}
