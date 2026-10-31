package com.dcb.controller;

import com.dcb.entity.TransactionStatusEntity;
import com.dcb.enums.TransactionStatus;
import com.dcb.enums.TransactionType;
import com.dcb.exception.BankException;
import com.dcb.model.APIResponse;
import com.dcb.model.AmountRequest;
import com.dcb.model.TransferRequest;
import com.dcb.service.AccountService;
import com.dcb.service.PdfService;
import com.dcb.service.TransactionService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class TransactionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final PdfService pdfService;
    private final Util util;

    @GetMapping("/api/v1/accounts/me")
    public ResponseEntity<APIResponse<?>> myAccount(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(accountService.summary(user.getUsername()), "Account details fetched."));
    }

    @GetMapping("/api/v1/accounts/me/balance")
    public ResponseEntity<APIResponse<?>> balance(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(accountService.summary(user.getUsername()), "Balance fetched."));
    }

    @PostMapping("/api/v1/transactions/deposit")
    public ResponseEntity<APIResponse<?>> deposit(@AuthenticationPrincipal UserDetails user,
                                                  @RequestHeader(IDEMPOTENCY_KEY) String key,
                                                  @RequestBody @Valid AmountRequest request) {
        var response = transactionService.deposit(user.getUsername(), request, key);
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Deposit successful (simulated)."));
    }

    @PostMapping("/api/v1/transactions/withdraw")
    public ResponseEntity<APIResponse<?>> withdraw(@AuthenticationPrincipal UserDetails user,
                                                   @RequestHeader(IDEMPOTENCY_KEY) String key,
                                                   @RequestBody @Valid AmountRequest request) {
        var response = transactionService.withdraw(user.getUsername(), request, key);
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Withdrawal successful (simulated)."));
    }

    @GetMapping("/api/v1/transactions/beneficiary")
    public ResponseEntity<APIResponse<?>> beneficiary(@AuthenticationPrincipal UserDetails user,
                                                      @RequestParam String accountNo) {
        return ResponseEntity.ok(util.getSuccessResponse(transactionService.lookupBeneficiary(user.getUsername(), accountNo.trim()), "Beneficiary found."));
    }

    @PostMapping("/api/v1/transactions/transfer")
    public ResponseEntity<APIResponse<?>> transfer(@AuthenticationPrincipal UserDetails user,
                                                   @RequestHeader(IDEMPOTENCY_KEY) String key,
                                                   @RequestBody @Valid TransferRequest request) {
        var response = transactionService.transfer(user.getUsername(), request, key);
        return ResponseEntity.status(Boolean.TRUE.equals(response.getReplayed()) ? HttpStatus.OK : HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "Transfer successful (simulated)."));
    }

    @GetMapping("/api/v1/transactions/history")
    public ResponseEntity<APIResponse<?>> history(@AuthenticationPrincipal UserDetails user,
                                                  @RequestParam(required = false) String reference,
                                                  @RequestParam(required = false) TransactionType type,
                                                  @RequestParam(required = false) TransactionStatus status,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        var filter = new TransactionService.HistoryFilter(null, reference, type, status, from, to);
        return ResponseEntity.ok(util.getSuccessResponse(transactionService.history(user.getUsername(), filter, page, size), "Transaction history fetched."));
    }

    @GetMapping("/api/v1/transactions/mini-statement")
    public ResponseEntity<APIResponse<?>> miniStatement(@AuthenticationPrincipal UserDetails user,
                                                        @RequestParam(defaultValue = "10") int count) {
        return ResponseEntity.ok(util.getSuccessResponse(transactionService.miniStatement(user.getUsername(), count), "Mini statement fetched."));
    }

    @GetMapping("/api/v1/transactions/statement/pdf")
    public ResponseEntity<byte[]> statementPdf(@AuthenticationPrincipal UserDetails user,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusMonths(1) : from;
        if (start.isAfter(end)) {
            throw new BankException("'from' date must be on or before 'to' date.");
        }
        if (start.isBefore(end.minusYears(1))) {
            throw new BankException("Statement period can be at most one year.");
        }
        String bankId = user.getUsername();
        var account = accountService.getAccount(bankId);
        byte[] pdf = pdfService.statement(Util.fullName(accountService.getUser(bankId)), account.getAccountNo(), start, end,
                account.getAccountBalance(), transactionService.statementEntries(account.getAccountNo(), start, end));
        return pdf("statement-" + start + "-to-" + end + ".pdf", pdf);
    }

    @GetMapping("/api/v1/transactions/{transactionId}")
    public ResponseEntity<APIResponse<?>> transaction(@AuthenticationPrincipal UserDetails user,
                                                      @PathVariable String transactionId) {
        return ResponseEntity.ok(util.getSuccessResponse(transactionService.getOwnTransaction(user.getUsername(), transactionId), "Transaction fetched."));
    }

    @GetMapping("/api/v1/transactions/{transactionId}/receipt")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal UserDetails user, @PathVariable String transactionId) {
        String bankId = user.getUsername();
        TransactionStatusEntity entry = transactionService.getOwnEntity(bankId, transactionId);
        return pdf("receipt-" + transactionId + ".pdf", pdfService.receipt(Util.fullName(accountService.getUser(bankId)), entry));
    }

    static ResponseEntity<byte[]> pdf(String fileName, byte[] bytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}
