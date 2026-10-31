package com.dcb;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransactionApiTest extends ApiTestSupport {

    @Test
    void depositAndWithdrawUpdateBalance() {
        Customer c = customer("1000.00");
        Res dep = post("/api/v1/transactions/deposit", c.token(), Map.of("amount", "500.00"), key());
        assertEquals(201, dep.status(), dep.message());
        assertEquals("SUCCESS", dep.data().path("status").asText());
        assertEquals(0, new BigDecimal("1500.00").compareTo(dep.data().path("balanceAfter").decimalValue()));

        Res wd = post("/api/v1/transactions/withdraw", c.token(), Map.of("amount", "200.50"), key());
        assertEquals(201, wd.status(), wd.message());
        assertEquals(0, new BigDecimal("1299.50").compareTo(balance(c.accountNo())));

        Res account = get("/api/v1/accounts/me", c.token());
        assertEquals(0, new BigDecimal("1299.50").compareTo(account.data().path("balance").decimalValue()));
    }

    @Test
    void insufficientWithdrawalIsRejectedAndRecordedAsFailed() {
        Customer c = customer("1000.00");
        Res wd = post("/api/v1/transactions/withdraw", c.token(), Map.of("amount", "5000.00"), key());
        assertEquals(422, wd.status());
        assertTrue(wd.message().contains("Insufficient balance"));
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(c.accountNo())));

        Res failed = get("/api/v1/transactions/history?status=FAILED", c.token());
        assertEquals(1, failed.data().path("totalElements").asInt());
    }

    @Test
    void invalidAmountsAreRejected() {
        Customer c = customer("1000.00");
        assertEquals(400, post("/api/v1/transactions/deposit", c.token(), Map.of("amount", "0"), key()).status());
        assertEquals(400, post("/api/v1/transactions/deposit", c.token(), Map.of("amount", "-10"), key()).status());
        assertEquals(400, post("/api/v1/transactions/deposit", c.token(), Map.of("amount", "10.123"), key()).status());
        Res noKey = post("/api/v1/transactions/deposit", c.token(), Map.of("amount", "10"), null);
        assertEquals(400, noKey.status());
        assertTrue(noKey.message().contains("Idempotency-Key"));
    }

    @Test
    void transferMovesMoneyAndWritesBothLedgerEntries() {
        Customer a = customer("1000.00");
        Customer b = customer("100.00");

        Res lookup = get("/api/v1/transactions/beneficiary?accountNo=" + b.accountNo(), a.token());
        assertEquals(200, lookup.status());
        assertEquals("ACTIVE", lookup.data().path("status").asText());
        assertTrue(lookup.data().path("holderName").asText().contains("*"), "holder name must be masked");
        assertTrue(lookup.data().path("bankId").isMissingNode(), "bank ID must not be exposed");

        Res tr = post("/api/v1/transactions/transfer", a.token(),
                Map.of("beneficiaryAccountNo", b.accountNo(), "amount", "300.00", "remarks", "rent"), key());
        assertEquals(201, tr.status(), tr.message());
        String reference = tr.data().path("reference").asText();
        assertEquals(0, new BigDecimal("700.00").compareTo(balance(a.accountNo())));
        assertEquals(0, new BigDecimal("400.00").compareTo(balance(b.accountNo())));

        Res credit = get("/api/v1/transactions/history?reference=" + reference, b.token());
        assertEquals(1, credit.data().path("totalElements").asInt());
        assertEquals("CREDIT", credit.data().path("content").get(0).path("direction").asText());
    }

    @Test
    void transferToOwnOrUnknownAccountIsRejected() {
        Customer a = customer("1000.00");
        assertEquals(400, post("/api/v1/transactions/transfer", a.token(),
                Map.of("beneficiaryAccountNo", a.accountNo(), "amount", "10"), key()).status());
        assertEquals(404, post("/api/v1/transactions/transfer", a.token(),
                Map.of("beneficiaryAccountNo", "99999999999", "amount", "10"), key()).status());
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance(a.accountNo())));
    }

    @Test
    void duplicateTransferWithSameKeyIsReplayedNotRepeated() {
        Customer a = customer("1000.00");
        Customer b = customer("0.00");
        String key = key();
        Map<String, String> body = Map.of("beneficiaryAccountNo", b.accountNo(), "amount", "250.00");

        Res first = post("/api/v1/transactions/transfer", a.token(), body, key);
        Res second = post("/api/v1/transactions/transfer", a.token(), body, key);
        assertEquals(201, first.status());
        assertEquals(200, second.status());
        assertTrue(second.data().path("replayed").asBoolean());
        assertEquals(first.data().path("reference").asText(), second.data().path("reference").asText());
        assertEquals(0, new BigDecimal("750.00").compareTo(balance(a.accountNo())));
        assertEquals(0, new BigDecimal("250.00").compareTo(balance(b.accountNo())));
    }

    @Test
    void failedTransferRollsBackBothAccounts() {
        Customer a = customer("100.00");
        Customer b = customer("100.00");
        Res tooMuch = post("/api/v1/transactions/transfer", a.token(),
                Map.of("beneficiaryAccountNo", b.accountNo(), "amount", "500.00"), key());
        assertEquals(422, tooMuch.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(balance(a.accountNo())));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance(b.accountNo())));

        // receiver frozen: rejected after both accounts are locked, nothing moves
        String admin = admin();
        assertEquals(200, put("/api/v1/admin/accounts/" + b.accountNo() + "/status", admin, Map.of("status", "FROZEN")).status());
        Res frozen = post("/api/v1/transactions/transfer", a.token(),
                Map.of("beneficiaryAccountNo", b.accountNo(), "amount", "50.00"), key());
        assertEquals(400, frozen.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(balance(a.accountNo())));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance(b.accountNo())));
    }

    @Test
    void historyFiltersPaginationAndMiniStatement() {
        Customer c = customer("1000.00");
        for (int i = 1; i <= 3; i++) {
            post("/api/v1/transactions/deposit", c.token(), Map.of("amount", i + "0.00"), key());
        }
        post("/api/v1/transactions/withdraw", c.token(), Map.of("amount", "5.00"), key());

        assertEquals(3, get("/api/v1/transactions/history?type=DEPOSIT", c.token()).data().path("totalElements").asInt());
        Res paged = get("/api/v1/transactions/history?page=0&size=2", c.token());
        assertEquals(2, paged.data().path("content").size());
        assertEquals(2, paged.data().path("totalPages").asInt());
        String tomorrow = LocalDate.now().plusDays(1).toString();
        assertEquals(0, get("/api/v1/transactions/history?from=" + tomorrow, c.token()).data().path("totalElements").asInt());
        assertEquals(400, get("/api/v1/transactions/history?from=" + tomorrow + "&to=" + LocalDate.now(), c.token()).status());

        Res mini = get("/api/v1/transactions/mini-statement?count=2", c.token());
        assertEquals(2, mini.data().size());
        assertEquals("WITHDRAWAL", mini.data().get(0).path("type").asText(), "latest first");
    }

    @Test
    void receiptAndStatementArePdfsAndOnlyForTheOwner() {
        Customer c = customer("1000.00");
        Customer other = customer("1000.00");
        String txnId = post("/api/v1/transactions/deposit", c.token(), Map.of("amount", "10"), key()).data().path("transactionId").asText();

        Res receipt = get("/api/v1/transactions/" + txnId + "/receipt", c.token());
        assertEquals(200, receipt.status());
        assertTrue(new String(receipt.raw(), 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"));

        Res statement = get("/api/v1/transactions/statement/pdf", c.token());
        assertEquals(200, statement.status());
        assertTrue(statement.contentType().contains("pdf"));

        assertEquals(404, get("/api/v1/transactions/" + txnId, other.token()).status());
        assertEquals(404, get("/api/v1/transactions/" + txnId + "/receipt", other.token()).status());
    }
}
