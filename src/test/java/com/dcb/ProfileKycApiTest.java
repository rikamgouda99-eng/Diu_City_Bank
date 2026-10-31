package com.dcb;

import com.dcb.entity.OtpEntity;
import com.dcb.repository.OtpRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfileKycApiTest extends ApiTestSupport {

    @Autowired
    OtpRepo otpRepo;

    @Test
    void retrieveAndUpdateProfileWithoutTouchingProtectedFields() {
        Customer c = customer("500.00");
        Res profile = get("/api/v1/profile", c.token());
        assertEquals(200, profile.status());
        assertEquals(c.bankId(), profile.data().path("bankId").asText());
        assertEquals(c.accountNo(), profile.data().path("accountNo").asText());
        assertEquals("NOT_SUBMITTED", profile.data().path("kycStatus").asText());
        assertTrue(profile.data().path("password").isMissingNode());

        Map<String, Object> update = new HashMap<>(Map.of("firstName", "Asha", "lastName", "Patel", "phoneNumber", "9123456789",
                "addressDetails", "Fort Road, Diu", "maritalStatus", "married", "gender", "Female"));
        update.put("bankId", "HACKED");
        update.put("role", "ADMIN");
        update.put("accountBalance", 99999999);
        Res updated = put("/api/v1/profile", c.token(), update);
        assertEquals(200, updated.status(), updated.message());
        assertEquals("Asha Patel", updated.data().path("fullName").asText());
        assertEquals(c.bankId(), updated.data().path("bankId").asText());
        assertEquals("CUSTOMER", updated.data().path("role").asText());
        assertEquals(0, balance(c.accountNo()).compareTo(new java.math.BigDecimal("500.00")));

        update.put("phoneNumber", "12");
        assertEquals(400, put("/api/v1/profile", c.token(), update).status());
    }

    @Test
    void profilePhotoUploadRetrieveReplaceAndDelete() {
        Customer c = customer("0.00");
        assertEquals(404, get("/api/v1/profile/photo", c.token()).status());

        byte[] png = png(2048);
        Res upload = multipart("/api/v1/profile/photo", c.token(), Map.of(), "me.png", "image/png", png);
        assertEquals(200, upload.status(), upload.message());
        assertTrue(upload.data().path("hasPhoto").asBoolean());

        Res photo = get("/api/v1/profile/photo", c.token());
        assertEquals(200, photo.status());
        assertEquals("image/png", photo.contentType());
        assertArrayEquals(png, photo.raw());

        byte[] jpeg = jpeg(1024);
        assertEquals(200, multipart("/api/v1/profile/photo", c.token(), Map.of(), "me.jpg", "image/jpeg", jpeg).status());
        Res replaced = get("/api/v1/profile/photo", c.token());
        assertEquals("image/jpeg", replaced.contentType());
        assertTrue(Arrays.equals(jpeg, replaced.raw()));

        assertEquals(200, delete("/api/v1/profile/photo", c.token()).status());
        assertEquals(404, get("/api/v1/profile/photo", c.token()).status());
    }

    @Test
    void invalidOrOversizedPhotoIsRejected() {
        Customer c = customer("0.00");
        Res text = multipart("/api/v1/profile/photo", c.token(), Map.of(), "evil.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertEquals(400, text.status());
        assertTrue(text.message().contains("Invalid file format"));

        Res big = multipart("/api/v1/profile/photo", c.token(), Map.of(), "big.png", "image/png", png(2 * 1024 * 1024 + 1));
        assertEquals(400, big.status());
        assertTrue(big.message().contains("too large"));
    }

    @Test
    void profileRequiresAuthentication() {
        assertEquals(401, get("/api/v1/profile", null).status());
        assertEquals(401, get("/api/v1/profile/photo", "not-a-real-token").status());
    }

    @Test
    void changePasswordChecksCurrentPassword() {
        Customer c = customer("0.00");
        assertEquals(400, put("/api/v1/profile/password", c.token(), Map.of("oldPassword", "wrong-pass", "newPassword", "NewSecret@1")).status());
        assertEquals(200, put("/api/v1/profile/password", c.token(), Map.of("oldPassword", PASSWORD, "newPassword", "NewSecret@1")).status());
        Res login = post("/api/v1/public/login", null, Map.of("bankId", c.bankId(), "password", "NewSecret@1"), null);
        assertEquals(200, login.status());
        assertEquals("CUSTOMER", login.data().path("role").asText());
    }

    @Test
    void emailChangeNeedsOtpFromTheNewAddress() {
        Customer c = customer("0.00");
        String newEmail = "new-" + c.bankId().toLowerCase() + "@test.local";
        assertEquals(200, post("/api/v1/profile/email", c.token(), Map.of("newEmail", newEmail), null).status());
        assertTrue(otpRepo.existsById(newEmail));

        // the real OTP went by mail; replace it with a known one for the test
        otpRepo.save(new OtpEntity(newEmail, encoder.encode("123456"), LocalDateTime.now().plusMinutes(10)));
        assertEquals(400, post("/api/v1/profile/email/verify", c.token(), Map.of("newEmail", newEmail, "otp", "654321"), null).status());
        Res ok = post("/api/v1/profile/email/verify", c.token(), Map.of("newEmail", newEmail, "otp", "123456"), null);
        assertEquals(200, ok.status(), ok.message());
        assertEquals(newEmail, ok.data().path("mailId").asText());
    }

    @Test
    void kycSubmitReviewAndStatus() {
        Customer c = customer("0.00");
        String admin = admin();

        Res badPan = multipart("/api/v1/kyc", c.token(), Map.of("documentType", "PAN", "documentNumber", "123"), "pan.pdf", "application/pdf", pdf());
        assertEquals(400, badPan.status());

        Res submitted = multipart("/api/v1/kyc", c.token(), Map.of("documentType", "PAN", "documentNumber", "ABCDE1234F"), "pan.pdf", "application/pdf", pdf());
        assertEquals(201, submitted.status(), submitted.message());
        assertEquals("PENDING", submitted.data().path("status").asText());
        assertEquals("XXXXXX234F", submitted.data().path("documentNumber").asText());
        long id = submitted.data().path("id").asLong();

        assertEquals(400, multipart("/api/v1/kyc", c.token(), Map.of("documentType", "PAN", "documentNumber", "ABCDE1234F"), "pan.pdf", "application/pdf", pdf()).status());
        assertEquals(400, put("/api/v1/admin/kyc/" + id + "/reject", admin, Map.of()).status(), "rejection needs a comment");

        Res doc = get("/api/v1/admin/kyc/" + id + "/document", admin);
        assertEquals(200, doc.status());
        assertTrue(doc.contentType().contains("pdf"));

        assertEquals(200, put("/api/v1/admin/kyc/" + id + "/approve", admin, Map.of("comment", "verified")).status());
        Res mine = get("/api/v1/kyc", c.token());
        assertEquals("APPROVED", mine.data().path("kycStatus").asText());
        assertEquals("verified", mine.data().path("latest").path("adminComment").asText());
    }
}
