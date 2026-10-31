package com.dcb.controller;

import com.dcb.model.APIResponse;
import com.dcb.model.EmiCalculationRequest;
import com.dcb.model.EmiPaymentRequest;
import com.dcb.model.LoanApplyRequest;
import com.dcb.service.LoanService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final Util util;

    @GetMapping("/rates")
    public ResponseEntity<APIResponse<?>> rates() {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.rates(), "Loan rates fetched."));
    }

    @PostMapping("/calculate-emi")
    public ResponseEntity<APIResponse<?>> calculate(@RequestBody @Valid EmiCalculationRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.calculate(request), "EMI calculated (simulation)."));
    }

    @PostMapping("/apply")
    public ResponseEntity<APIResponse<?>> apply(@AuthenticationPrincipal UserDetails user, @RequestBody @Valid LoanApplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(loanService.apply(user.getUsername(), request), "Loan application submitted (simulation)."));
    }

    @GetMapping
    public ResponseEntity<APIResponse<?>> list(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.list(user.getUsername()), "Loans fetched."));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<APIResponse<?>> get(@AuthenticationPrincipal UserDetails user, @PathVariable String loanId) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.get(user.getUsername(), loanId), "Loan fetched."));
    }

    @GetMapping("/{loanId}/schedule")
    public ResponseEntity<APIResponse<?>> schedule(@AuthenticationPrincipal UserDetails user, @PathVariable String loanId) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.schedule(user.getUsername(), loanId), "EMI schedule fetched."));
    }

    @PostMapping("/{loanId}/emi/pay")
    public ResponseEntity<APIResponse<?>> payEmi(@AuthenticationPrincipal UserDetails user, @PathVariable String loanId,
                                                 @RequestHeader("Idempotency-Key") String key,
                                                 @RequestBody(required = false) @Valid EmiPaymentRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.payEmi(user.getUsername(), loanId, request, key), "EMI paid (simulated)."));
    }

    @GetMapping("/{loanId}/emi/history")
    public ResponseEntity<APIResponse<?>> emiHistory(@AuthenticationPrincipal UserDetails user, @PathVariable String loanId) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.emiHistory(user.getUsername(), loanId), "EMI payment history fetched."));
    }
}
