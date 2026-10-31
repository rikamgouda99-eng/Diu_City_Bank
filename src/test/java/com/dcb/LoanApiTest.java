package com.dcb;

import com.dcb.entity.BankAccountEntity;
import com.dcb.service.calc.EmiCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoanApiTest extends ApiTestSupport {

    private Map<String, Object> application(String amount, int months) {
        return Map.of("loanType", "PERSONAL", "amount", amount, "tenureMonths", months,
                "employmentType", "SALARIED", "employerName", "Diu Port Trust", "monthlyIncome", "60000");
    }

    @Test
    void emiCalculatorMatchesFormula() {
        Customer c = customer("0.00");
        Res res = post("/api/v1/loans/calculate-emi", c.token(),
                Map.of("loanType", "HOME", "amount", "100000", "annualInterestRate", "10", "tenureMonths", 12), null);
        assertEquals(200, res.status(), res.message());
        assertEquals(0, new BigDecimal("8791.59").compareTo(res.data().path("monthlyEmi").decimalValue()));
        assertTrue(res.data().path("disclaimer").asText().contains("SIMULATION"));

        Res defaultRate = post("/api/v1/loans/calculate-emi", c.token(),
                Map.of("loanType", "PERSONAL", "amount", "100000", "tenureMonths", 12), null);
        assertEquals(0, new BigDecimal("11.50").compareTo(defaultRate.data().path("annualInterestRate").decimalValue()));
    }

    @Test
    void applyApproveScheduleAndPayEmi() {
        Customer c = customer("1000.00");
        String admin = admin();
        Res applied = post("/api/v1/loans/apply", c.token(), application("60000", 12), null);
        assertEquals(201, applied.status(), applied.message());
        assertEquals("PENDING", applied.data().path("status").asText());
        String loanId = applied.data().path("loanId").asText();
        assertEquals(400, post("/api/v1/loans/apply", c.token(), application("20000", 12), null).status(), "one pending application");

        assertEquals(400, put("/api/v1/admin/loans/" + loanId + "/approve", admin, Map.of("approvedAmount", "90000")).status(),
                "can't approve more than requested");
        Res approved = put("/api/v1/admin/loans/" + loanId + "/approve", admin, Map.of("approvedAmount", "50000", "interestRate", "12"));
        assertEquals(200, approved.status(), approved.message());
        assertEquals("ACTIVE", approved.data().path("status").asText());
        BigDecimal emi = EmiCalculator.emi(new BigDecimal("50000"), new BigDecimal("12"), 12);
        assertEquals(0, emi.compareTo(approved.data().path("monthlyEmi").decimalValue()));
        assertEquals(0, new BigDecimal("51000.00").compareTo(balance(c.accountNo())), "disbursed to savings");

        Res schedule = get("/api/v1/loans/" + loanId + "/schedule", c.token());
        assertEquals(12, schedule.data().size());
        assertEquals(0, schedule.data().get(11).path("remainingPrincipal").decimalValue().signum());

        String key = key();
        Res paid = post("/api/v1/loans/" + loanId + "/emi/pay", c.token(), Map.of("emiNumber", 1), key);
        assertEquals(200, paid.status(), paid.message());
        assertEquals("PAID", paid.data().path("status").asText());
        BigDecimal afterFirst = balance(c.accountNo());
        assertEquals(0, new BigDecimal("51000.00").subtract(emi).compareTo(afterFirst));

        Res replay = post("/api/v1/loans/" + loanId + "/emi/pay", c.token(), Map.of("emiNumber", 1), key);
        assertTrue(replay.data().path("replayed").asBoolean());
        assertEquals(0, afterFirst.compareTo(balance(c.accountNo())), "duplicate key doesn't pay twice");
        Res again = post("/api/v1/loans/" + loanId + "/emi/pay", c.token(), Map.of("emiNumber", 1), key());
        assertEquals(400, again.status());
        assertTrue(again.message().contains("already paid"));

        Res loan = get("/api/v1/loans/" + loanId, c.token());
        assertEquals(1, loan.data().path("emisPaid").asInt());
        assertEquals(11, loan.data().path("emisPending").asInt());
        assertEquals(schedule.data().get(1).path("dueDate").asText(), loan.data().path("nextEmiDueDate").asText());
        assertEquals(1, get("/api/v1/loans/" + loanId + "/emi/history", c.token()).data().size());

        BankAccountEntity account = accountRepo.findById(c.accountNo()).orElseThrow();
        account.setAccountBalance(new BigDecimal("10.00"));
        accountRepo.save(account);
        assertEquals(422, post("/api/v1/loans/" + loanId + "/emi/pay", c.token(), null, key()).status());
        assertEquals(0, new BigDecimal("10.00").compareTo(balance(c.accountNo())));
    }

    @Test
    void rejectLoanNeedsReason() {
        Customer c = customer("0.00");
        String admin = admin();
        String loanId = post("/api/v1/loans/apply", c.token(), application("30000", 24), null).data().path("loanId").asText();
        assertEquals(400, put("/api/v1/admin/loans/" + loanId + "/reject", admin, Map.of()).status());
        Res rejected = put("/api/v1/admin/loans/" + loanId + "/reject", admin, Map.of("comment", "income too low"));
        assertEquals("REJECTED", rejected.data().path("status").asText());
        assertEquals(400, post("/api/v1/loans/" + loanId + "/emi/pay", c.token(), null, key()).status());
    }
}
