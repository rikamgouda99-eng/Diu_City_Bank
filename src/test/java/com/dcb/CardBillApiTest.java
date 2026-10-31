package com.dcb;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CardBillApiTest extends ApiTestSupport {

    private long issue(Customer c, String admin, String type, Map<String, Object> approval) {
        Res applied = post("/api/v1/cards/apply", c.token(), Map.of("cardType", type, "cardScheme", "VISA"), null);
        assertEquals(201, applied.status(), applied.message());
        assertEquals("APPLIED", applied.data().path("status").asText());
        long id = applied.data().path("cardId").asLong();
        Res approved = put("/api/v1/admin/cards/" + id + "/approve", admin, approval);
        assertEquals(200, approved.status(), approved.message());
        return id;
    }

    @Test
    void debitCardLifecycleAndSimulation() {
        Customer c = customer("10000.00");
        String admin = admin();
        long id = issue(c, admin, "DEBIT", Map.of());
        assertEquals(400, post("/api/v1/cards/apply", c.token(), Map.of("cardType", "DEBIT", "cardScheme", "RUPAY"), null).status(),
                "only one debit card");

        Res card = get("/api/v1/cards/" + id, c.token());
        assertEquals("ACTIVE", card.data().path("status").asText());
        assertTrue(card.data().path("maskedNumber").asText().matches("XXXX XXXX XXXX \\d{4}"));
        String raw = new String(card.raw(), StandardCharsets.UTF_8).toLowerCase();
        assertFalse(raw.contains("cvv") || raw.contains("pinhash") || raw.contains("cardtoken"), "no secrets in API");

        Map<String, Object> purchase = Map.of("merchantName", "Diu Mart", "amount", "100.00", "pin", "4826");
        assertTrue(post("/api/v1/cards/" + id + "/simulate-transaction", c.token(), purchase, key()).message().contains("set a PIN"));

        assertEquals(400, put("/api/v1/cards/" + id + "/pin", c.token(), Map.of("newPin", "1234")).status(), "weak PIN");
        assertEquals(200, put("/api/v1/cards/" + id + "/pin", c.token(), Map.of("newPin", "4826")).status());
        assertEquals(400, put("/api/v1/cards/" + id + "/pin", c.token(), Map.of("oldPin", "0000", "newPin", "5937")).status());

        Res ok = post("/api/v1/cards/" + id + "/simulate-transaction", c.token(), purchase, key());
        assertEquals(201, ok.status(), ok.message());
        assertEquals(0, new BigDecimal("9900.00").compareTo(balance(c.accountNo())));
        assertEquals(400, post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Diu Mart", "amount", "100.00", "pin", "9999"), key()).status());

        assertEquals(200, put("/api/v1/admin/cards/" + id + "/limit", admin, Map.of("cardLimit", "1000")).status());
        Res overLimit = post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Gadget Store", "amount", "5000.00", "pin", "4826"), key());
        assertEquals(422, overLimit.status());
        assertTrue(overLimit.message().contains("Daily card limit"));
        Res limit = get("/api/v1/cards/" + id + "/limit", c.token());
        assertEquals(0, new BigDecimal("900.00").compareTo(limit.data().path("available").decimalValue()));

        Res txns = get("/api/v1/cards/" + id + "/transactions", c.token());
        assertEquals(2, txns.data().path("totalElements").asInt(), "one success, one failed attempt");

        assertEquals(200, put("/api/v1/cards/" + id + "/block", c.token(), null).status());
        assertEquals(400, post("/api/v1/cards/" + id + "/simulate-transaction", c.token(), purchase, key()).status());
        assertEquals(200, put("/api/v1/cards/" + id + "/unblock", c.token(), null).status());

        assertEquals(200, put("/api/v1/admin/cards/" + id + "/block", admin, null).status());
        assertEquals(400, put("/api/v1/cards/" + id + "/unblock", c.token(), null).status(), "bank block can't be lifted by customer");
    }

    @Test
    void creditCardUsesCreditLineNotBalance() {
        Customer c = customer("5000.00");
        String admin = admin();
        long id = issue(c, admin, "CREDIT", Map.of("cardLimit", "10000"));
        put("/api/v1/cards/" + id + "/pin", c.token(), Map.of("newPin", "4826"));

        Res buy = post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Air Ticket", "amount", "3000.00", "pin", "4826"), key());
        assertEquals(201, buy.status(), buy.message());
        assertEquals(0, new BigDecimal("7000.00").compareTo(buy.data().path("availableAfter").decimalValue()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(balance(c.accountNo())));

        assertEquals(422, post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Hotel", "amount", "8000.00", "pin", "4826"), key()).status());

        Res dues = post("/api/v1/cards/" + id + "/pay-dues", c.token(), Map.of("amount", "1000.00"), key());
        assertEquals(200, dues.status(), dues.message());
        assertEquals(0, new BigDecimal("4000.00").compareTo(balance(c.accountNo())));
        Res card = get("/api/v1/cards/" + id, c.token());
        assertEquals(0, new BigDecimal("2000.00").compareTo(card.data().path("creditUsed").decimalValue()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(card.data().path("billing").path("totalOutstanding").decimalValue()));
    }

    @Test
    void rejectedApplicationAndReplacement() {
        Customer c = customer("1000.00");
        String admin = admin();
        long applied = post("/api/v1/cards/apply", c.token(), Map.of("cardType", "CREDIT", "cardScheme", "MASTERCARD"), null)
                .data().path("cardId").asLong();
        Res rejected = put("/api/v1/admin/cards/" + applied + "/reject", admin, Map.of("comment", "low income"));
        assertEquals("REJECTED", rejected.data().path("status").asText());

        long id = issue(c, admin, "DEBIT", Map.of());
        Res replacement = post("/api/v1/cards/" + id + "/replace", c.token(), null, null);
        assertEquals(201, replacement.status(), replacement.message());
        long newId = replacement.data().path("cardId").asLong();
        assertEquals("BLOCKED", get("/api/v1/cards/" + id, c.token()).data().path("status").asText());
        put("/api/v1/admin/cards/" + newId + "/approve", admin, Map.of());
        assertEquals("REPLACED", get("/api/v1/cards/" + id, c.token()).data().path("status").asText());
        assertEquals("ACTIVE", get("/api/v1/cards/" + newId, c.token()).data().path("status").asText());
    }

    @Test
    void billPaymentsByUpiAndCreditCard() {
        Customer c = customer("2000.00");
        String admin = admin();
        Res upi = post("/api/v1/bills/pay", c.token(), Map.of("billType", "ELECTRICITY", "consumerNumber", "EL-778899",
                "amount", "450.00", "paymentMode", "UPI"), key());
        assertEquals(201, upi.status(), upi.message());
        assertNotNull(upi.data().path("transactionId").asText(null));
        assertEquals(0, new BigDecimal("1550.00").compareTo(balance(c.accountNo())));

        assertEquals(400, post("/api/v1/bills/pay", c.token(), Map.of("billType", "WATER", "consumerNumber", "W1234",
                "amount", "100", "paymentMode", "CREDIT_CARD"), key()).status(), "card details required");

        long card = issue(c, admin, "CREDIT", Map.of("cardLimit", "5000"));
        put("/api/v1/cards/" + card + "/pin", c.token(), Map.of("newPin", "4826"));
        Res byCard = post("/api/v1/bills/pay", c.token(), Map.of("billType", "MOBILE_RECHARGE", "consumerNumber", "9876543210",
                "amount", "299.00", "paymentMode", "CREDIT_CARD", "cardId", card, "cardPin", "4826"), key());
        assertEquals(201, byCard.status(), byCard.message());
        assertEquals(0, new BigDecimal("1550.00").compareTo(balance(c.accountNo())), "credit card doesn't touch balance");

        assertEquals(422, post("/api/v1/bills/pay", c.token(), Map.of("billType", "INTERNET", "consumerNumber", "NET-1",
                "amount", "99999.00", "paymentMode", "BANK_DRAFT"), key()).status());

        Res history = get("/api/v1/bills/history", c.token());
        assertEquals(2, history.data().path("totalElements").asInt());
        assertEquals(200, get("/api/v1/admin/bills?bankId=" + c.bankId(), admin).status());
    }
}
