package com.dcb.controller;

import com.dcb.model.APIResponse;
import com.dcb.model.BillPaymentRequest;
import com.dcb.service.BillService;
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
