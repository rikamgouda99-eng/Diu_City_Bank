package com.dcb.controller;

import com.dcb.enums.CardStatus;
import com.dcb.enums.CardType;
import com.dcb.enums.FdStatus;
import com.dcb.enums.LoanStatus;
import com.dcb.model.*;
import com.dcb.service.CardService;
import com.dcb.service.FixedDepositService;
import com.dcb.service.LoanService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin approval and management of fixed deposits, cards and loans. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final FixedDepositService fdService;
    private final CardService cardService;
    private final LoanService loanService;
    private final Util util;

    // ---------------------------------------------------------------- fixed deposits

    @GetMapping("/fd")
    public ResponseEntity<APIResponse<?>> fds(@RequestParam(required = false) FdStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.adminList(status, page, size), "Fixed deposits fetched."));
    }

    @GetMapping("/fd/{fdId}")
    public ResponseEntity<APIResponse<?>> fd(@PathVariable String fdId) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.adminGet(fdId), "Fixed deposit fetched."));
    }

    @PutMapping("/fd/{fdId}/approve")
    public ResponseEntity<APIResponse<?>> approveFd(@PathVariable String fdId, @RequestBody(required = false) @Valid FdApprovalRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.approve(fdId, request), "FD approved."));
    }

    @PutMapping("/fd/{fdId}/reject")
    public ResponseEntity<APIResponse<?>> rejectFd(@PathVariable String fdId, @RequestBody(required = false) @Valid DecisionRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(fdService.reject(fdId, request), "FD rejected and principal refunded."));
    }

    @PostMapping("/fd/process-maturity")
    public ResponseEntity<APIResponse<?>> processMaturity() {
        int paid = fdService.processMaturities();
        return ResponseEntity.ok(util.getSuccessResponse(Map.of("paidOut", paid), paid + " matured FD(s) paid out."));
    }

    // ---------------------------------------------------------------- cards

    @GetMapping("/cards")
    public ResponseEntity<APIResponse<?>> cards(@RequestParam(required = false) CardStatus status,
                                                @RequestParam(required = false) CardType cardType,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.adminList(status, cardType, page, size), "Cards fetched."));
    }

    @GetMapping("/cards/{cardId}")
    public ResponseEntity<APIResponse<?>> card(@PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.adminGet(cardId), "Card fetched."));
    }

    @PutMapping("/cards/{cardId}/approve")
    public ResponseEntity<APIResponse<?>> approveCard(@PathVariable Long cardId, @RequestBody(required = false) @Valid CardApprovalRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.approve(cardId, request == null ? new CardApprovalRequest() : request), "Card approved and issued."));
    }

    @PutMapping("/cards/{cardId}/reject")
    public ResponseEntity<APIResponse<?>> rejectCard(@PathVariable Long cardId, @RequestBody(required = false) @Valid DecisionRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.reject(cardId, request), "Card application rejected."));
    }

    @PutMapping("/cards/{cardId}/block")
    public ResponseEntity<APIResponse<?>> blockCard(@PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.adminBlock(cardId), "Card blocked."));
    }

    @PutMapping("/cards/{cardId}/unblock")
    public ResponseEntity<APIResponse<?>> unblockCard(@PathVariable Long cardId) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.adminUnblock(cardId), "Card unblocked."));
    }

    @PutMapping("/cards/{cardId}/limit")
    public ResponseEntity<APIResponse<?>> cardLimit(@PathVariable Long cardId, @RequestBody @Valid CardLimitUpdateRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.updateLimit(cardId, request), "Card limit updated."));
    }

    @GetMapping("/cards/{cardId}/transactions")
    public ResponseEntity<APIResponse<?>> cardTransactions(@PathVariable Long cardId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(cardService.adminTransactions(cardId, page, size), "Card transactions fetched."));
    }

    // ---------------------------------------------------------------- loans

    @GetMapping("/loans")
    public ResponseEntity<APIResponse<?>> loans(@RequestParam(required = false) LoanStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.adminList(status, page, size), "Loans fetched."));
    }

    @GetMapping("/loans/emi-payments")
    public ResponseEntity<APIResponse<?>> emiPayments(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.adminAllEmiPayments(page, size), "EMI payments fetched."));
    }

    @GetMapping("/loans/{loanId}")
    public ResponseEntity<APIResponse<?>> loan(@PathVariable String loanId) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.adminGet(loanId), "Loan fetched."));
    }

    @GetMapping("/loans/{loanId}/schedule")
    public ResponseEntity<APIResponse<?>> loanSchedule(@PathVariable String loanId) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.adminSchedule(loanId), "EMI schedule fetched."));
    }

    @GetMapping("/loans/{loanId}/emi/history")
    public ResponseEntity<APIResponse<?>> loanEmiHistory(@PathVariable String loanId) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.adminEmiHistory(loanId), "EMI payment history fetched."));
    }

    @PutMapping("/loans/{loanId}/approve")
    public ResponseEntity<APIResponse<?>> approveLoan(@PathVariable String loanId, @RequestBody(required = false) @Valid LoanApprovalRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.approve(loanId, request), "Loan approved and disbursed (simulated)."));
    }

    @PutMapping("/loans/{loanId}/reject")
    public ResponseEntity<APIResponse<?>> rejectLoan(@PathVariable String loanId, @RequestBody(required = false) @Valid DecisionRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(loanService.reject(loanId, request), "Loan rejected."));
    }
}
