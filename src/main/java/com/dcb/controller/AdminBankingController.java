package com.dcb.controller;

import com.dcb.enums.*;
import com.dcb.model.*;
import com.dcb.service.*;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Admin management of customers, accounts, transactions, bills, KYC and customer requests.
 * Everything under /api/v1/admin requires ROLE_ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminBankingController {

    private final AdminBankingService adminBankingService;
    private final TransactionService transactionService;
    private final BillService billService;
    private final KycService kycService;
    private final CustomerRequestService requestService;
    private final Util util;

    @GetMapping("/dashboard")
    public ResponseEntity<APIResponse<?>> dashboard() {
        return ResponseEntity.ok(util.getSuccessResponse(adminBankingService.dashboard(), "Dashboard fetched."));
    }

    @GetMapping("/customers")
    public ResponseEntity<APIResponse<?>> customers(@RequestParam(required = false) String search,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(adminBankingService.customers(search, page, size), "Customers fetched."));
    }

    @GetMapping("/customers/{bankId}")
    public ResponseEntity<APIResponse<?>> customer(@PathVariable String bankId) {
        return ResponseEntity.ok(util.getSuccessResponse(adminBankingService.customer(bankId), "Customer fetched."));
    }

    @GetMapping("/accounts")
    public ResponseEntity<APIResponse<?>> accounts(@RequestParam(required = false) String search,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(adminBankingService.accounts(search, page, size), "Accounts fetched."));
    }

    @PutMapping("/accounts/{accountNo}/status")
    public ResponseEntity<APIResponse<?>> accountStatus(@PathVariable String accountNo, @RequestBody @Valid AccountStatusRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(adminBankingService.setAccountStatus(accountNo, request), "Account status updated."));
    }

    @GetMapping("/transactions")
    public ResponseEntity<APIResponse<?>> transactions(@RequestParam(required = false) String accountNo,
                                                       @RequestParam(required = false) String reference,
                                                       @RequestParam(required = false) TransactionType type,
                                                       @RequestParam(required = false) TransactionStatus status,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        var filter = new TransactionService.HistoryFilter(Util.blankToNull(accountNo), reference, type, status, from, to);
        return ResponseEntity.ok(util.getSuccessResponse(transactionService.search(filter, page, size), "Transactions fetched."));
    }

    @GetMapping("/bills")
    public ResponseEntity<APIResponse<?>> bills(@RequestParam(required = false) BillType billType,
                                                @RequestParam(required = false) String bankId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(billService.adminSearch(billType, bankId, page, size), "Bill payments fetched."));
    }

    @GetMapping("/kyc")
    public ResponseEntity<APIResponse<?>> kyc(@RequestParam(required = false) KycStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(kycService.adminList(status, page, size), "KYC requests fetched."));
    }

    @GetMapping("/kyc/{id}/document")
    public ResponseEntity<byte[]> kycDocument(@PathVariable Long id) {
        return KycController.document(kycService.adminDocument(id));
    }

    @PutMapping("/kyc/{id}/approve")
    public ResponseEntity<APIResponse<?>> approveKyc(@AuthenticationPrincipal UserDetails admin, @PathVariable Long id,
                                                     @RequestBody(required = false) @Valid DecisionRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(kycService.review(id, true, admin.getUsername(), request), "KYC approved."));
    }

    @PutMapping("/kyc/{id}/reject")
    public ResponseEntity<APIResponse<?>> rejectKyc(@AuthenticationPrincipal UserDetails admin, @PathVariable Long id,
                                                    @RequestBody(required = false) @Valid DecisionRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(kycService.review(id, false, admin.getUsername(), request), "KYC rejected."));
    }

    @GetMapping("/requests")
    public ResponseEntity<APIResponse<?>> requests(@RequestParam(required = false) RequestStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(requestService.adminList(status, page, size), "Customer requests fetched."));
    }

    @PutMapping("/requests/{id}/approve")
    public ResponseEntity<APIResponse<?>> approveRequest(@PathVariable Long id, @RequestBody(required = false) @Valid DecisionRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(requestService.decide(id, true, request), "Request approved."));
    }

    @PutMapping("/requests/{id}/reject")
    public ResponseEntity<APIResponse<?>> rejectRequest(@PathVariable Long id, @RequestBody(required = false) @Valid DecisionRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(requestService.decide(id, false, request), "Request rejected."));
    }
}
