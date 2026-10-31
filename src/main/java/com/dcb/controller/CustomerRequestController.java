package com.dcb.controller;

import com.dcb.model.APIResponse;
import com.dcb.model.AccountClosureRequest;
import com.dcb.model.CustomerRequestCreate;
import com.dcb.service.CustomerRequestService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class CustomerRequestController {

    private final CustomerRequestService requestService;
    private final Util util;

    @PostMapping
    public ResponseEntity<APIResponse<?>> create(@AuthenticationPrincipal UserDetails user, @RequestBody @Valid CustomerRequestCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(requestService.create(user.getUsername(), request), "Request submitted."));
    }

    @PostMapping("/account-closure")
    public ResponseEntity<APIResponse<?>> closeAccount(@AuthenticationPrincipal UserDetails user, @RequestBody @Valid AccountClosureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(requestService.requestAccountClosure(user.getUsername(), request), "Account closure request submitted for review."));
    }

    @GetMapping
    public ResponseEntity<APIResponse<?>> mine(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(requestService.mine(user.getUsername()), "Requests fetched."));
    }
}
