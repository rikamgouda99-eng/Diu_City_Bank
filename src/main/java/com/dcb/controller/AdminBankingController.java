package com.dcb.controller;

import com.dcb.enums.*;
import com.dcb.model.*;
import com.dcb.service.*;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    private final NotificationService notifier;
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
        var response = adminBankingService.setAccountStatus(accountNo, request);
        notifier.notify(response.getBankId(), "Account status updated",
                "The bank has changed the status of your account.",
                NotificationService.details()
                        .add("Account", Util.maskAccount(response.getAccountNo()))
                        .add("New status", response.getStatus())
                        .add("Reason", response.getStatusReason()));
        return ResponseEntity.ok(util.getSuccessResponse(response, "Account status updated."));
    }

    /** Cash deposit and withdrawal are branch operations, so only an admin can post them. */
    @PostMapping("/accounts/{accountNo}/deposit")
    public ResponseEntity<APIResponse<?>> deposit(@AuthenticationPrincipal UserDetails admin, @PathVariable String accountNo,
                                                  @RequestHeader("Idempotency-Key") String key,
                                                  @RequestBody @Valid AmountRequest request) {
        var response = transactionService.deposit(admin.getUsername(), accountNo.trim(), request, key);
        if (!Boolean.TRUE.equals(response.getReplayed())) notifyCash(accountNo, "Amount deposited", "was deposited into", response);
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Deposit successful."));
    }

    @PostMapping("/accounts/{accountNo}/withdraw")
    public ResponseEntity<APIResponse<?>> withdraw(@AuthenticationPrincipal UserDetails admin, @PathVariable String accountNo,
                                                   @RequestHeader("Idempotency-Key") String key,
                                                   @RequestBody @Valid AmountRequest request) {
        var response = transactionService.withdraw(admin.getUsername(), accountNo.trim(), request, key);
        if (!Boolean.TRUE.equals(response.getReplayed())) notifyCash(accountNo, "Amount withdrawn", "was withdrawn from", response);
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Withdrawal successful."));
    }

    private void notifyCash(String accountNo, String subject, String verb, TransactionResponse response) {
        notifier.notify(transactionService.ownerOf(accountNo.trim()), subject,
                NotificationService.money(response.getAmount()) + " " + verb + " your account " + Util.maskAccount(response.getAccountNo()) + " at the branch.",
                NotificationService.details()
                        .amount("Amount", response.getAmount())
                        .add("Transaction ID", response.getTransactionId())
                        .add("Reference", response.getReference())
                        .add("Description", response.getDescription())
                        .amount("Available balance", response.getBalanceAfter()));
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
        var response = kycService.review(id, true, admin.getUsername(), request);
        notifyKyc(response);
        return ResponseEntity.ok(util.getSuccessResponse(response, "KYC approved."));
    }

    @PutMapping("/kyc/{id}/reject")
    public ResponseEntity<APIResponse<?>> rejectKyc(@AuthenticationPrincipal UserDetails admin, @PathVariable Long id,
                                                    @RequestBody(required = false) @Valid DecisionRequest request) {
        var response = kycService.review(id, false, admin.getUsername(), request);
        notifyKyc(response);
        return ResponseEntity.ok(util.getSuccessResponse(response, "KYC rejected."));
    }

    @GetMapping("/requests")
    public ResponseEntity<APIResponse<?>> requests(@RequestParam(required = false) RequestStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(requestService.adminList(status, page, size), "Customer requests fetched."));
    }

    @PutMapping("/requests/{id}/approve")
    public ResponseEntity<APIResponse<?>> approveRequest(@PathVariable Long id, @RequestBody(required = false) @Valid DecisionRequest request) {
        var response = requestService.decide(id, true, request);
        notifyRequest(response);
        return ResponseEntity.ok(util.getSuccessResponse(response, "Request approved."));
    }

    @PutMapping("/requests/{id}/reject")
    public ResponseEntity<APIResponse<?>> rejectRequest(@PathVariable Long id, @RequestBody(required = false) @Valid DecisionRequest request) {
        var response = requestService.decide(id, false, request);
        notifyRequest(response);
        return ResponseEntity.ok(util.getSuccessResponse(response, "Request rejected."));
    }

    private void notifyKyc(KycResponse kyc) {
        notifier.notify(kyc.getBankId(), "KYC " + kyc.getStatus().toLowerCase(),
                "The bank has reviewed your KYC document.",
                NotificationService.details()
                        .add("Document", kyc.getDocumentType())
                        .add("Status", kyc.getStatus())
                        .add("Bank comment", kyc.getAdminComment()));
    }

    private void notifyRequest(CustomerRequestResponse r) {
        notifier.notify(r.getBankId(), "Request " + r.getStatus().toLowerCase(),
                "The bank has responded to your service request.",
                NotificationService.details()
                        .add("Request", r.getSubject())
                        .add("Type", r.getRequestType())
                        .add("Status", r.getStatus())
                        .add("Bank comment", r.getAdminComment()));
    }
}
