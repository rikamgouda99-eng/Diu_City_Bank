package com.dcb;

import com.dcb.entity.FixedDepositEntity;
import com.dcb.repository.FixedDepositRepo;
import com.dcb.service.calc.InterestCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FixedDepositApiTest extends ApiTestSupport {

    @Autowired
    FixedDepositRepo fdRepo;

    @Test
    void quoteUsesConfiguredRateAndCompoundFormula() {
        Customer c = customer("0.00");
        Res quote = post("/api/v1/fd/quote", c.token(), Map.of("amount", "10000", "tenureMonths", 12), null);
        assertEquals(200, quote.status(), quote.message());
        assertEquals(0, new BigDecimal("6.50").compareTo(quote.data().path("interestRate").decimalValue()));
        BigDecimal expected = InterestCalculator.maturityAmount(new BigDecimal("10000"), new BigDecimal("6.50"), 12, 4);
        assertEquals(0, expected.compareTo(quote.data().path("maturityAmount").decimalValue()));

        assertEquals(400, post("/api/v1/fd/quote", c.token(), Map.of("amount", "10000", "tenureMonths", 2), null).status());
        assertEquals(400, post("/api/v1/fd/quote", c.token(), Map.of("amount", "100", "tenureMonths", 12), null).status());
        assertEquals(400, post("/api/v1/fd/quote", c.token(), Map.of("amount", "10000", "tenureMonths", 12, "interestRate", "25"), null).status());
    }

    @Test
    void createApproveAndPrematureCloseWithinMinimumDaysReturnsPrincipal() {
        Customer c = customer("50000.00");
        String admin = admin();
        Res created = post("/api/v1/fd", c.token(), Map.of("amount", "20000", "tenureMonths", 24), key());
        assertEquals(201, created.status(), created.message());
        assertEquals("PENDING", created.data().path("status").asText());
        String fdId = created.data().path("fdId").asText();
        assertEquals(0, new BigDecimal("30000.00").compareTo(balance(c.accountNo())));

        assertEquals(200, put("/api/v1/admin/fd/" + fdId + "/approve", admin, Map.of()).status());
        assertEquals(400, put("/api/v1/admin/fd/" + fdId + "/approve", admin, Map.of()).status(), "can't approve twice");

        Res preview = get("/api/v1/fd/" + fdId + "/close-preview", c.token());
        assertEquals("PREMATURE", preview.data().path("closureType").asText());
        assertEquals(0, new BigDecimal("20000.00").compareTo(preview.data().path("payoutAmount").decimalValue()));

        Res closed = post("/api/v1/fd/" + fdId + "/close", c.token(), null, key());
        assertEquals(200, closed.status(), closed.message());
        assertEquals("CLOSED", closed.data().path("status").asText());
        assertEquals(0, new BigDecimal("50000.00").compareTo(balance(c.accountNo())));
        assertEquals(400, post("/api/v1/fd/" + fdId + "/close", c.token(), null, key()).status(), "can't close twice");

        Res summary = get("/api/v1/fd/summary", c.token());
        assertEquals(1, summary.data().path("closedFds").asInt());
    }

    @Test
    void fdNeedsSufficientBalance() {
        Customer c = customer("500.00");
        Res res = post("/api/v1/fd", c.token(), Map.of("amount", "5000", "tenureMonths", 12), key());
        assertEquals(422, res.status());
        assertEquals(0, new BigDecimal("500.00").compareTo(balance(c.accountNo())));
        assertEquals(0, get("/api/v1/fd", c.token()).data().size());
    }

    @Test
    void rejectRefundsPrincipalAndReversesBooking() {
        Customer c = customer("10000.00");
        String admin = admin();
        String fdId = post("/api/v1/fd", c.token(), Map.of("amount", "4000", "tenureMonths", 12), key()).data().path("fdId").asText();
        assertEquals(400, put("/api/v1/admin/fd/" + fdId + "/reject", admin, Map.of()).status(), "reason required");

        Res rejected = put("/api/v1/admin/fd/" + fdId + "/reject", admin, Map.of("comment", "documents missing"));
        assertEquals(200, rejected.status(), rejected.message());
        assertEquals("REJECTED", rejected.data().path("status").asText());
        assertEquals(0, new BigDecimal("10000.00").compareTo(balance(c.accountNo())));

        Res booking = get("/api/v1/transactions/history?type=FD_BOOKING", c.token());
        assertEquals("REVERSED", booking.data().path("content").get(0).path("status").asText());
    }

    @Test
    void maturityIsPaidExactlyOnce() {
        Customer c = customer("10000.00");
        String admin = admin();
        String fdId = post("/api/v1/fd", c.token(), Map.of("amount", "10000", "tenureMonths", 12), key()).data().path("fdId").asText();
        put("/api/v1/admin/fd/" + fdId + "/approve", admin, Map.of());

        // pretend the FD started a year ago
        FixedDepositEntity fd = fdRepo.findById(fdId).orElseThrow();
        fd.setDepositDate(LocalDate.now().minusYears(1));
        fd.setMaturityDate(LocalDate.now().minusDays(1));
        fdRepo.save(fd);

        Res first = post("/api/v1/admin/fd/process-maturity", admin, null, null);
        assertTrue(first.data().path("paidOut").asInt() >= 1);
        Res matured = get("/api/v1/fd/" + fdId, c.token());
        assertEquals("MATURED", matured.data().path("status").asText());
        BigDecimal maturity = matured.data().path("maturityAmount").decimalValue();
        assertEquals(0, maturity.compareTo(balance(c.accountNo())));

        post("/api/v1/admin/fd/process-maturity", admin, null, null);
        assertEquals(400, post("/api/v1/fd/" + fdId + "/close", c.token(), null, key()).status());
        assertEquals(0, maturity.compareTo(balance(c.accountNo())), "no second payout");
    }

    @Test
    void customersCannotSeeOtherCustomersFds() {
        Customer a = customer("5000.00");
        Customer b = customer("5000.00");
        String fdId = post("/api/v1/fd", a.token(), Map.of("amount", "1000", "tenureMonths", 12), key()).data().path("fdId").asText();
        assertEquals(404, get("/api/v1/fd/" + fdId, b.token()).status());
        assertEquals(404, post("/api/v1/fd/" + fdId + "/close", b.token(), null, key()).status());
    }
}
