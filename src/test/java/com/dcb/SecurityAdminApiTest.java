package com.dcb;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAdminApiTest extends ApiTestSupport {

    @Test
    void customersCannotCallAdminApis() {
        Customer c = customer("100.00");
        assertEquals(403, get("/api/v1/admin/dashboard", c.token()).status());
        assertEquals(403, get("/api/v1/admin/transactions", c.token()).status());
        assertEquals(403, put("/api/v1/admin/accounts/" + c.accountNo() + "/status", c.token(), Map.of("status", "ACTIVE")).status());
        assertEquals(403, get("/api/v1/admin/approvals", c.token()).status());
    }

    @Test
    void adminsCannotUseCustomerBankingApis() {
        String admin = admin();
        assertEquals(403, post("/api/v1/transactions/deposit", admin, Map.of("amount", "100"), key()).status());
        assertEquals(403, get("/api/v1/cards", admin).status());
        assertEquals(200, get("/api/v1/profile", admin).status(), "profile is shared");
    }

    @Test
    void anonymousRequestsAreUnauthorized() {
        assertEquals(401, get("/api/v1/accounts/me", null).status());
        assertEquals(401, post("/api/v1/transactions/transfer", null, Map.of(), key()).status());
        assertEquals(401, get("/api/v1/admin/dashboard", null).status());
    }

    @Test
    void adminDashboardCustomersAndTransactions() {
        Customer c = customer("100.00");
        String admin = admin();
        post("/api/v1/transactions/deposit", c.token(), Map.of("amount", "50"), key());

        Res dashboard = get("/api/v1/admin/dashboard", admin);
        assertEquals(200, dashboard.status());
        assertTrue(dashboard.data().path("totalCustomers").asLong() >= 1);

        Res customers = get("/api/v1/admin/customers?search=" + c.bankId(), admin);
        assertEquals(1, customers.data().path("totalElements").asInt());
        assertEquals(c.accountNo(), customers.data().path("content").get(0).path("accountNo").asText());

        Res txns = get("/api/v1/admin/transactions?accountNo=" + c.accountNo(), admin);
        assertEquals(1, txns.data().path("totalElements").asInt());

        Res approvals = get("/api/v1/admin/approvals", admin);
        assertEquals(200, approvals.status());
        assertFalse(new String(approvals.raw(), StandardCharsets.UTF_8).contains("\"password\""), "password hash never leaves the server");
    }

    @Test
    void accountClosureRequestApprovedByAdminClosesAccount() {
        Customer c = customer("100.00");
        String admin = admin();
        assertEquals(400, post("/api/v1/requests/account-closure", c.token(), Map.of("password", "wrong", "reason", "moving"), null).status());
        Res request = post("/api/v1/requests/account-closure", c.token(), Map.of("password", PASSWORD, "reason", "Moving to another bank"), null);
        assertEquals(201, request.status(), request.message());
        long id = request.data().path("id").asLong();

        Res cheque = post("/api/v1/requests", c.token(), Map.of("requestType", "CHEQUE_BOOK", "subject", "25 leaf cheque book"), null);
        assertEquals(201, cheque.status());
        assertEquals(2, get("/api/v1/requests", c.token()).data().size());

        assertEquals(200, put("/api/v1/admin/requests/" + id + "/approve", admin, Map.of("comment", "closed")).status());
        assertEquals("CLOSED", get("/api/v1/accounts/me", c.token()).data().path("status").asText());
        Res deposit = post("/api/v1/transactions/deposit", c.token(), Map.of("amount", "10"), key());
        assertEquals(400, deposit.status());
        assertTrue(deposit.message().contains("closed"));
    }

    @Test
    void loginResponseCarriesRoleForTheFrontend() {
        Customer c = customer("0.00");
        Res login = post("/api/v1/public/login", null, Map.of("bankId", c.bankId(), "password", PASSWORD), null);
        assertEquals("CUSTOMER", login.data().path("role").asText());
        assertFalse(login.data().path("name").asText().isBlank());
        assertEquals(400, post("/api/v1/public/login", null, Map.of("bankId", c.bankId(), "password", "nope"), null).status());
    }
}
