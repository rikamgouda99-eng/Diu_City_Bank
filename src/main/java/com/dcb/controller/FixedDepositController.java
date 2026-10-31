package com.dcb.controller;

import com.dcb.model.APIResponse;
import com.dcb.model.FdCreateRequest;
import com.dcb.service.FixedDepositService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fd")
@RequiredArgsConstructor
public class FixedDepositController {

    private final FixedDepositService fdService;
    private final Util util;

    @GetMapping("/rates")
    public ResponseEntity<APIResponse<?>> rates() {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.rates(), "FD rates fetched."));
    }

    @PostMapping("/quote")
    public ResponseEntity<APIResponse<?>> quote(@RequestBody @Valid FdCreateRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.quote(request), "FD maturity calculated (simulation)."));
    }

    @PostMapping
    public ResponseEntity<APIResponse<?>> create(@AuthenticationPrincipal UserDetails user,
                                                 @RequestHeader("Idempotency-Key") String key,
                                                 @RequestBody @Valid FdCreateRequest request) {
        var response = fdService.create(user.getUsername(), request, key);
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "FD booked and sent for approval (simulated)."));
    }

    @GetMapping
    public ResponseEntity<APIResponse<?>> list(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.list(user.getUsername()), "Fixed deposits fetched."));
    }

    @GetMapping("/summary")
    public ResponseEntity<APIResponse<?>> summary(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.summary(user.getUsername()), "FD summary fetched."));
    }

    @GetMapping("/{fdId}")
    public ResponseEntity<APIResponse<?>> get(@AuthenticationPrincipal UserDetails user, @PathVariable String fdId) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.get(user.getUsername(), fdId), "Fixed deposit fetched."));
    }

    @GetMapping("/{fdId}/maturity")
    public ResponseEntity<APIResponse<?>> maturity(@AuthenticationPrincipal UserDetails user, @PathVariable String fdId) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.maturity(user.getUsername(), fdId), "Maturity details fetched."));
    }

    @GetMapping("/{fdId}/close-preview")
    public ResponseEntity<APIResponse<?>> closePreview(@AuthenticationPrincipal UserDetails user, @PathVariable String fdId) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.closePreview(user.getUsername(), fdId), "Closure estimate calculated."));
    }

    @PostMapping("/{fdId}/close")
    public ResponseEntity<APIResponse<?>> close(@AuthenticationPrincipal UserDetails user,
                                                @RequestHeader("Idempotency-Key") String key,
                                                @PathVariable String fdId) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.close(user.getUsername(), fdId, key), "FD closed and amount credited (simulated)."));
    }
}
