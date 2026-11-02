package com.dcb;

import org.junit.jupiter.api.Test;

import com.dcb.entity.CardEntity;
import com.dcb.enums.CardType;
import com.dcb.model.CardBillingResponse;
import com.dcb.repository.CardRepo;
import com.dcb.service.CardService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CardBillApiTest extends ApiTestSupport {

    @Autowired
    CardService cardService;

    @Autowired
    CardRepo cardRepo;

    private static boolean luhn(String number) {
        int sum = 0;
        for (int i = 0; i < number.length(); i++) {
            int d = number.charAt(number.length() - 1 - i) - '0';
            if (i % 2 == 1) d = d * 2 > 9 ? d * 2 - 9 : d * 2;
            sum += d;
        }
        return sum % 10 == 0;
    }

    @Test
    void statementAndDueDatesFollowTheBillingCycle() {
        // statement on the 1st of every month, payment due 20 days later (defaults)
        CardEntity card = CardEntity.builder().cardType(CardType.CREDIT).creditUsed(new BigDecimal("1000.00"))
                .issuedDate(LocalDate.of(2026, 10, 15)).build();

        CardBillingResponse beforeFirst = cardService.billing(card, LocalDate.of(2026, 10, 20));
        assertEquals(LocalDate.of(2026, 11, 1), beforeFirst.getStatementDate(), "no statement from before the card was issued");
        assertEquals(LocalDate.of(2026, 11, 21), beforeFirst.getPaymentDueDate());

        CardBillingResponse open = cardService.billing(card, LocalDate.of(2026, 11, 10));
        assertEquals(LocalDate.of(2026, 11, 1), open.getStatementDate(), "latest statement is still open");
        assertEquals(LocalDate.of(2026, 11, 21), open.getPaymentDueDate());
        assertEquals(11, open.getDaysToDue());

        CardBillingResponse onDueDate = cardService.billing(card, LocalDate.of(2026, 11, 21));
        assertEquals(LocalDate.of(2026, 11, 21), onDueDate.getPaymentDueDate());
        assertEquals(0, onDueDate.getDaysToDue());

        CardBillingResponse afterDue = cardService.billing(card, LocalDate.of(2026, 11, 25));
        assertEquals(LocalDate.of(2026, 12, 1), afterDue.getStatementDate(), "moves on to the next cycle");
        assertEquals(LocalDate.of(2026, 12, 21), afterDue.getPaymentDueDate());

        CardBillingResponse yearEnd = cardService.billing(card, LocalDate.of(2026, 12, 30));
        assertEquals(LocalDate.of(2027, 1, 1), yearEnd.getStatementDate());
        assertEquals(LocalDate.of(2027, 1, 21), yearEnd.getPaymentDueDate());
    }

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
        assertFalse(raw.contains("cvv") || raw.contains("pinhash") || raw.contains("cardtoken") || raw.contains("panencrypted"), "no secrets in API");

        Res number = get("/api/v1/cards/" + id + "/number", c.token());
        assertEquals(200, number.status(), number.message());
        String full = number.data().path("cardNumber").asText();
        assertTrue(full.matches("4\\d{3} \\d{4} \\d{4} \\d{4}"), full);
        assertTrue(full.endsWith(card.data().path("maskedNumber").asText().substring(15)), "same card as the masked number");
        assertEquals(404, get("/api/v1/cards/" + id + "/number", customer("0.00").token()).status(), "only the owner can see it");

        // cards issued before full numbers were stored still get one that keeps their last four digits
        CardEntity old = cardRepo.findById(id).orElseThrow();
        old.setPanEncrypted(null);
        cardRepo.save(old);
        String restored = get("/api/v1/cards/" + id + "/number", c.token()).data().path("cardNumber").asText();
        assertTrue(restored.matches("4\\d{3} \\d{4} \\d{4} \\d{4}"), restored);
        assertTrue(restored.endsWith(full.substring(15)), "keeps the last four digits");
        assertTrue(luhn(restored.replace(" ", "")), "valid card number");
        assertEquals(restored, get("/api/v1/cards/" + id + "/number", c.token()).data().path("cardNumber").asText(), "same number every time");

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

        Map<String, Object> ticket = Map.of("merchantName", "Air Ticket", "amount", "3000.00", "pin", "4826");
        assertTrue(post("/api/v1/cards/" + id + "/simulate-transaction", c.token(), ticket, key()).message().contains("OTP"), "OTP required");
        String otp = cardOtp(c, id);
        Res wrongPin = post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Air Ticket", "amount", "3000.00", "pin", "5937", "otp", otp), key());
        assertTrue(wrongPin.message().contains("PIN"), wrongPin.message());
        Res wrongOtp = post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Air Ticket", "amount", "3000.00", "pin", "4826", "otp", "111111"), key());
        assertTrue(wrongOtp.message().contains("Incorrect OTP"), wrongOtp.message());

        Res buy = post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Air Ticket", "amount", "3000.00", "pin", "4826", "otp", otp), key());
        assertEquals(201, buy.status(), buy.message());
        assertEquals(0, new BigDecimal("7000.00").compareTo(buy.data().path("availableAfter").decimalValue()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(balance(c.accountNo())));
        assertTrue(post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Air Ticket", "amount", "10.00", "pin", "4826", "otp", otp), key()).message().contains("request an OTP"),
                "an OTP can be used only once");

        assertEquals(422, post("/api/v1/cards/" + id + "/simulate-transaction", c.token(),
                Map.of("merchantName", "Hotel", "amount", "8000.00", "pin", "4826", "otp", cardOtp(c, id)), key()).status());

        assertEquals(400, post("/api/v1/cards/" + id + "/pay-dues", c.token(), Map.of("amount", "1000.00"), key()).status(), "PIN and OTP required");
        Res dues = post("/api/v1/cards/" + id + "/pay-dues", c.token(),
                Map.of("amount", "1000.00", "pin", "4826", "otp", cardOtp(c, id)), key());
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
    void billPaymentsByBankDraftAndCreditCard() {
        Customer c = customer("2000.00");
        String admin = admin();
        assertFalse(get("/api/v1/bills/options", c.token()).data().path("paymentModes").toString().contains("UPI"), "UPI is not offered");
        Res upi = post("/api/v1/bills/pay", c.token(), Map.of("billType", "ELECTRICITY", "consumerNumber", "EL-778899",
                "billerName", "Tata Power", "amount", "450.00", "paymentMode", "UPI"), key());
        assertEquals(400, upi.status(), "UPI is rejected");

        Res draft = post("/api/v1/bills/pay", c.token(), Map.of("billType", "ELECTRICITY", "consumerNumber", "EL-778899",
                "billerName", "Tata Power", "amount", "450.00", "paymentMode", "BANK_DRAFT"), key());
        assertEquals(201, draft.status(), draft.message());
        assertNotNull(draft.data().path("transactionId").asText(null));
        assertEquals(0, new BigDecimal("1550.00").compareTo(balance(c.accountNo())));

        assertEquals(400, post("/api/v1/bills/pay", c.token(), Map.of("billType", "WATER", "consumerNumber", "W1234",
                "billerName", "Diu Municipal Council", "amount", "100", "paymentMode", "CREDIT_CARD"), key()).status(), "card details required");

        Res badMobile = post("/api/v1/bills/pay", c.token(), Map.of("billType", "MOBILE_RECHARGE", "consumerNumber", "12345",
                "billerName", "Jio", "amount", "299.00", "paymentMode", "BANK_DRAFT"), key());
        assertTrue(badMobile.message().contains("Mobile number"), badMobile.message());
        Res noOperator = post("/api/v1/bills/pay", c.token(), Map.of("billType", "MOBILE_RECHARGE", "consumerNumber", "9876543210",
                "amount", "299.00", "paymentMode", "BANK_DRAFT"), key());
        assertTrue(noOperator.message().contains("network provider"), noOperator.message());

        long card = issue(c, admin, "CREDIT", Map.of("cardLimit", "5000"));
        put("/api/v1/cards/" + card + "/pin", c.token(), Map.of("newPin", "4826"));
        Res byCard = post("/api/v1/bills/pay", c.token(), Map.of("billType", "MOBILE_RECHARGE", "consumerNumber", "9876543210",
                "billerName", "Airtel", "amount", "299.00", "paymentMode", "CREDIT_CARD", "cardId", card, "cardPin", "4826", "otp", cardOtp(c, card)), key());
        assertEquals(201, byCard.status(), byCard.message());
        assertEquals(0, new BigDecimal("1550.00").compareTo(balance(c.accountNo())), "credit card doesn't touch balance");

        assertEquals(422, post("/api/v1/bills/pay", c.token(), Map.of("billType", "INTERNET", "consumerNumber", "NET-1",
                "billerName", "JioFiber", "amount", "99999.00", "paymentMode", "BANK_DRAFT"), key()).status());

        Res history = get("/api/v1/bills/history", c.token());
        assertEquals(2, history.data().path("totalElements").asInt());
        assertEquals(200, get("/api/v1/admin/bills?bankId=" + c.bankId(), admin).status());
    }
}
