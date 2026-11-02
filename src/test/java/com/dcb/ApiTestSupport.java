package com.dcb;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.CardOtpEntity;
import com.dcb.entity.UserEntity;
import com.dcb.enums.AccountStatus;
import com.dcb.repository.BankAccountRepo;
import com.dcb.repository.CardOtpRepo;
import com.dcb.repository.UserRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Starts the app on a random port and talks to it over HTTP with real JWTs, like the frontend does. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestSupport {

    static final String PASSWORD = "Secret@123";
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Value("${local.server.port}")
    int port;

    @Autowired
    UserRepo userRepo;

    @Autowired
    BankAccountRepo accountRepo;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    CardOtpRepo cardOtpRepo;

    private String teller;

    record Res(int status, JsonNode body, byte[] raw, String contentType) {
        JsonNode data() {
            return body.path("data");
        }

        String message() {
            return body.path("message").asText();
        }
    }

    record Customer(String bankId, String accountNo, String token) {
    }

    Customer customer(String balance) {
        long n = SEQ.incrementAndGet();
        String bankId = "TST" + n;
        userRepo.save(UserEntity.builder()
                .bankId(bankId)
                .mailId("customer" + n + "@test.local")
                .firstName("Test")
                .lastName("Customer" + n)
                .addressDetails("Diu")
                .phoneNumber("9876543210")
                .gender("Other")
                .maritalStatus("single")
                .dob(LocalDate.of(1995, 1, 1))
                .createdDate(LocalDate.now())
                .updatedDate(LocalDate.now())
                .isActive(true)
                .isVerified(true)
                .isApproved(true)
                .status("APPROVED BY ADMIN")
                .role("CUSTOMER")
                .password(encoder.encode(PASSWORD))
                .build());
        BankAccountEntity account = new BankAccountEntity();
        account.setAccountNo(String.valueOf(3567_0000_0000_0000L + n));
        account.setCustomerId(bankId);
        account.setAccountBalance(new BigDecimal(balance));
        account.setAccountType("SAVINGS");
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedAt(LocalDateTime.now());
        accountRepo.save(account);
        return new Customer(bankId, account.getAccountNo(), login(bankId));
    }

    String admin() {
        long n = SEQ.incrementAndGet();
        String bankId = "ADM" + n;
        userRepo.save(UserEntity.builder()
                .bankId(bankId)
                .mailId("admin" + n + "@test.local")
                .firstName("Bank")
                .lastName("Admin")
                .dob(LocalDate.of(1990, 1, 1))
                .createdDate(LocalDate.now())
                .updatedDate(LocalDate.now())
                .isActive(true)
                .isVerified(true)
                .isApproved(true)
                .role("ADMIN")
                .password(encoder.encode(PASSWORD))
                .build());
        return login(bankId);
    }

    String login(String bankId) {
        Res res = post("/api/v1/public/login", null, Map.of("bankId", bankId, "password", PASSWORD), null);
        assertEquals(200, res.status(), res.message());
        return res.data().path("accessToken").asText();
    }

    /** Cash deposit/withdrawal are admin-only, so tests post them through a shared admin. */
    Res cash(String kind, Customer c, String amount) {
        if (teller == null) teller = admin();
        return post("/api/v1/admin/accounts/" + c.accountNo() + "/" + kind, teller, Map.of("amount", amount), key());
    }

    /** Requests a credit card payment OTP and swaps the mailed value for a known one. */
    String cardOtp(Customer c, long cardId) {
        cardOtpRepo.deleteById(cardId);
        Res sent = post("/api/v1/cards/" + cardId + "/otp", c.token(), null, null);
        assertEquals(200, sent.status(), sent.message());
        CardOtpEntity otp = cardOtpRepo.findById(cardId).orElseThrow();
        otp.setOtpHash(encoder.encode("246810"));
        cardOtpRepo.save(otp);
        return "246810";
    }

    BigDecimal balance(String accountNo) {
        return accountRepo.findById(accountNo).orElseThrow().getAccountBalance();
    }

    static String key() {
        return UUID.randomUUID().toString();
    }

    Res get(String path, String token) {
        return send(base(path, token).GET().build());
    }

    Res delete(String path, String token) {
        return send(base(path, token).DELETE().build());
    }

    Res post(String path, String token, Object body, String idempotencyKey) {
        return send(withKey(base(path, token), idempotencyKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json(body))).build());
    }

    Res put(String path, String token, Object body) {
        return send(base(path, token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json(body))).build());
    }

    Res multipart(String path, String token, Map<String, String> fields, String fileName, String fileType, byte[] file) {
        String boundary = "----dcb" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fields.forEach((k, v) -> out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + k + "\"\r\n\r\n" + v + "\r\n")
                .getBytes(StandardCharsets.UTF_8)));
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: " + fileType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(file);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(base(path, token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build());
    }

    private HttpRequest.Builder base(String path, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private static HttpRequest.Builder withKey(HttpRequest.Builder b, String key) {
        return key == null ? b : b.header("Idempotency-Key", key);
    }

    private static String json(Object body) {
        try {
            return body == null ? "" : JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Res send(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String type = response.headers().firstValue("Content-Type").orElse("");
            JsonNode body = type.contains("json") && response.body().length > 0
                    ? JSON.readTree(response.body()) : JSON.createObjectNode();
            return new Res(response.statusCode(), body, response.body(), type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Smallest valid PNG header followed by padding. */
    static byte[] png(int size) {
        byte[] b = new byte[Math.max(size, 16)];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, b, 0, sig.length);
        return b;
    }

    static byte[] jpeg(int size) {
        byte[] b = new byte[Math.max(size, 16)];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        return b;
    }

    static byte[] pdf() {
        return "%PDF-1.4\n% test document\n".getBytes(StandardCharsets.US_ASCII);
    }
}
