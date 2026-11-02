package com.dcb.controller;

import com.dcb.model.APIResponse;
import com.dcb.model.AccountClosureRequest;
import com.dcb.model.CustomerRequestCreate;
import com.dcb.service.CustomerRequestService;
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
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class CustomerRequestController {

    private final CustomerRequestService requestService;
    private final NotificationService notifier;
    private final Util util;

    @PostMapping
    public ResponseEntity<APIResponse<?>> create(@AuthenticationPrincipal UserDetails user, @RequestBody @Valid CustomerRequestCreate request) {
        var response = requestService.create(user.getUsername(), request);
        notifier.notify(user.getUsername(), "Request submitted", "We received your service request.",
                NotificationService.details().add("Request", response.getSubject()).add("Type", response.getRequestType()).add("Status", response.getStatus()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Request submitted."));
    }

    @PostMapping("/account-closure")
    public ResponseEntity<APIResponse<?>> closeAccount(@AuthenticationPrincipal UserDetails user, @RequestBody @Valid AccountClosureRequest request) {
        var response = requestService.requestAccountClosure(user.getUsername(), request);
        notifier.notify(user.getUsername(), "Account closure requested", "We received your request to close your account. The bank will review it.",
                NotificationService.details().add("Status", response.getStatus()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Account closure request submitted for review."));
    }

    @GetMapping
    public ResponseEntity<APIResponse<?>> mine(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(requestService.mine(user.getUsername()), "Requests fetched."));
    }
}
