package com.dcb.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    private static final int SEND_ATTEMPTS = 3;

    /** Optional hidden archive copy of transactional mails. Never used for OTP mails. */
    @Value("${mail.bcc:}")
    private String bcc;

    /** Sender address. Defaults to the SMTP login; set it when the provider's login is not an email (e.g. SendGrid "apikey"). */
    @Value("${mail.from:${spring.mail.username}}")
    private String from;

    @Value("${mail.from-name:DIU City Bank}")
    private String fromName;

    @Value("${mail.reply-to:}")
    private String replyTo;

    /**
     * Brevo API key. When set, mails go out over HTTPS through Brevo's API instead of SMTP.
     * Needed on hosts that block outgoing SMTP ports, such as Render's free plan.
     */
    @Value("${mail.brevo.api-key:}")
    private String brevoApiKey;

    private final RestClient brevo = RestClient.create("https://api.brevo.com/v3");

    public void sendOtpMail(String email, String otp) {
        if (email == null || otp == null) {
            throw new IllegalArgumentException("Email or OTP cannot be null");
        }
        log.info("Sending mail to email: {}",maskEmail(email));

        String subject = "Your DIU City Bank verification code";

        String message = "<html><body style='font-family:Arial,Helvetica,sans-serif; color:#333333;'>"
                + "<h2 style='color:#17365d;'>Verify your email</h2>"
                + "<p>Hello,</p>"
                + "<p>Use this one-time verification code to continue with DIU City Bank:</p>"
                + "<h1 style='color:#2e6c80; letter-spacing:4px;'>" + otp + "</h1>"
                + "<p>This code is valid for <b>" + OtpService.VALIDITY_MINUTES + " minutes</b>. After that it expires and you can request a new one.</p>"
                + "<p>Never share this code with anyone. Bank staff will never ask for it.</p>"
                + "<p>If you did not request this code, you can ignore this email.</p>"
                + "<p>Regards,<br/><b>DIU City Bank</b></p>"
                + "</body></html>";

        send(email, subject, message, false);
    }

    public void sendAccountOpenMail(String email, String name, String accountNumber) {
        if (email == null) {
            throw new IllegalArgumentException("Mail ID cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be empty");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number cannot be empty");
        }

        log.info("Sending account opening confirmation to: {}", maskEmail(email));

        String subject = "Account Opening Confirmation | DIU CITY BANK";

        String safeName = escapeHtml(name);
        String safeAccountNumber = escapeHtml(accountNumber);

        String message = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Account Opening Confirmation</title>
        </head>
        <body style="margin:0; padding:0; background-color:#f4f6f8;
                     font-family:Arial,Helvetica,sans-serif; color:#333333;">

            <div style="max-width:600px; margin:30px auto; background:#ffffff;
                        border-radius:8px; overflow:hidden;
                        border:1px solid #e5e7eb;">

                <div style="background:#17365d; padding:25px 30px;
                            text-align:center; color:#ffffff;">
                    <h1 style="margin:0; font-size:24px;">
                        DIU CITY BANK
                    </h1>
                    <p style="margin:8px 0 0; font-size:14px;">
                        Account Opening Confirmation
                    </p>
                </div>

                <div style="padding:30px; line-height:1.7;">

                    <h2 style="color:#17365d; margin-top:0;">
                        Welcome, %s!
                    </h2>

                    <p>
                        Congratulations! Your bank account has been
                        successfully opened with DIU CITY BANK.
                    </p>

                    <p>
                        Your account details are provided below.
                        Please keep your account information secure.
                    </p>

                    <div style="background:#f0f5fa; border-left:4px solid #2e6c80;
                                padding:18px; margin:25px 0;
                                border-radius:4px;">
                        <p style="margin:0 0 8px; font-size:14px;
                                  color:#555555;">
                            Your Account Number
                        </p>
                        <p style="margin:0; font-size:24px; font-weight:bold;
                                  color:#17365d; overflow-wrap:anywhere;">
                            %s
                        </p>
                    </div>

                    <p>
                        You can sign in through our official banking
                        website to access your account and available services.
                    </p>

                    <p style="font-size:14px; color:#555555;">
                        <strong>Security reminder:</strong>
                        Never share your password, OTP, PIN, or other
                        authentication details with anyone.
                    </p>

                    <p>
                        If you did not request this account, please contact
                        our bank through its official support channels.
                    </p>

                    <p style="margin-top:30px;">
                        Thank you for choosing us.<br>
                        <strong style="color:#17365d;">DIU CITY BANK</strong>
                    </p>
                </div>

                <div style="background:#f4f6f8; padding:15px 25px;
                            text-align:center; font-size:12px;
                            color:#777777;">
                    This is an automated account notification.
                    Please do not reply directly to this email.
                </div>
            </div>
        </body>
        </html>
        """.formatted(safeName, safeAccountNumber);

        sendMimeMail(email, subject, message);
    }

    public void sendAccountOpeningRejectedMail(
            String email, String name, String reason) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email address cannot be empty");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be empty");
        }

        if (reason == null || reason.isBlank()) {
            reason = "Your application did not meet the bank's account opening requirements.";
        }

        log.info("Sending account opening rejection email to: {}", maskEmail(email));

        String subject = "Account Opening Request Update | DIU CITY BANK";

        String safeName = escapeHtml(name);
        String safeReason = escapeHtml(reason);

        String message = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Account Opening Request Update</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f6f8;
                         font-family:Arial,Helvetica,sans-serif; color:#333333;">

                <div style="max-width:600px; margin:30px auto; background:#ffffff;
                            border-radius:8px; overflow:hidden;
                            border:1px solid #e5e7eb;">

                    <div style="background:#17365d; padding:25px 30px;
                                text-align:center; color:#ffffff;">
                        <h1 style="margin:0; font-size:24px;">
                            DIU CITY BANK
                        </h1>
                        <p style="margin:8px 0 0; font-size:14px;">
                            Account Opening Request Update
                        </p>
                    </div>

                    <div style="padding:30px; line-height:1.7;">

                        <h2 style="color:#b42318; margin-top:0;">
                            Account Opening Request Rejected
                        </h2>

                        <p>Dear %s,</p>

                        <p>
                            Thank you for choosing DIU CITY BANK and for your
                            interest in opening an account with us.
                        </p>

                        <p>
                            After reviewing your application, we regret to inform
                            you that we are unable to approve your account opening
                            request at this time.
                        </p>

                        <div style="background:#fff4f2;
                                    border-left:4px solid #b42318;
                                    padding:18px; margin:25px 0;
                                    border-radius:4px;">
                            <p style="margin:0 0 8px; font-size:14px;
                                      color:#7a271a; font-weight:bold;">
                                Reason for rejection
                            </p>
                            <p style="margin:0; color:#333333;">
                                %s
                            </p>
                        </div>

                        <p>
                            If you believe this decision was made in error or
                            require further clarification, please contact our
                            official customer support team.
                        </p>

                        <p>
                            You may submit a new application if permitted by
                            the bank's account opening policies and applicable
                            requirements.
                        </p>

                        <p style="margin-top:30px;">
                            Thank you for your understanding.<br>
                            <strong style="color:#17365d;">DIU CITY BANK</strong>
                        </p>
                    </div>

                    <div style="background:#f4f6f8; padding:15px 25px;
                                text-align:center; font-size:12px;
                                color:#777777;">
                        This is an automated notification.
                        Please do not reply directly to this email.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(safeName, safeReason);

        sendMimeMail(email, subject, message);
    }

    public void sendCardOtpMail(String email, String otp, String maskedCard, int validityMinutes) {
        log.info("Sending card payment OTP to: {}", maskEmail(email));
        String message = "<html><body>"
                + "<h2>Credit card payment OTP</h2>"
                + "<p>Dear Customer,</p>"
                + "<p>Use this One-Time Password to confirm the payment on your credit card <b>" + escapeHtml(maskedCard) + "</b>.</p>"
                + "<h1 style='color: #2e6c80;'>" + otp + "</h1>"
                + "<p><b>Note:</b> This OTP is valid for <b>" + validityMinutes + " minutes</b> and can be used only once.</p>"
                + "<p>Never share this OTP with anyone, bank staff will never ask for it. "
                + "If you did not start this payment, please block your card and contact the bank.</p>"
                + "<br/><p>Best Regards,<br/><b>DIU CITY BANK</b></p>"
                + "</body></html>";
        send(email, "Your DIU City Bank credit card payment code", message, false);
    }

    /** Confirmation mail for a transaction or account action, with its details as a table. */
    public void sendNotificationMail(String email, String name, String subject, String headline, java.util.Map<String, String> details) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email address cannot be empty");
        }
        log.info("Sending '{}' notification to: {}", subject, maskEmail(email));

        StringBuilder rows = new StringBuilder();
        details.forEach((label, value) -> rows.append("""
                <tr>
                    <td style="padding:8px 0; color:#555555; font-size:14px;">%s</td>
                    <td style="padding:8px 0; text-align:right; font-weight:bold; color:#17365d; overflow-wrap:anywhere;">%s</td>
                </tr>
                """.formatted(escapeHtml(label), escapeHtml(value))));

        String message = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><title>%s</title></head>
            <body style="margin:0; padding:0; background-color:#f4f6f8;
                         font-family:Arial,Helvetica,sans-serif; color:#333333;">
                <div style="max-width:600px; margin:30px auto; background:#ffffff;
                            border-radius:8px; overflow:hidden; border:1px solid #e5e7eb;">
                    <div style="background:#17365d; padding:25px 30px; text-align:center; color:#ffffff;">
                        <h1 style="margin:0; font-size:24px;">DIU CITY BANK</h1>
                        <p style="margin:8px 0 0; font-size:14px;">%s</p>
                    </div>
                    <div style="padding:30px; line-height:1.7;">
                        <p>Dear %s,</p>
                        <p>%s</p>
                        <table style="width:100%%; border-collapse:collapse; margin:20px 0;
                                      border-top:1px solid #e5e7eb; border-bottom:1px solid #e5e7eb;">
                            %s
                        </table>
                        <p style="font-size:14px; color:#555555;">
                            <strong>Not you?</strong> If you did not perform this action, please
                            contact the bank immediately and change your password.
                        </p>
                        <p style="margin-top:30px;">Thank you for banking with us.<br>
                            <strong style="color:#17365d;">DIU CITY BANK</strong></p>
                    </div>
                    <div style="background:#f4f6f8; padding:15px 25px; text-align:center;
                                font-size:12px; color:#777777;">
                        This is an automated notification. Please do not reply directly to this email.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(escapeHtml(subject), escapeHtml(subject), escapeHtml(name == null ? "Customer" : name),
                escapeHtml(headline), rows);

        sendMimeMail(email, subject + " | DIU City Bank", message);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Transactional mail with an archive BCC copy when mail.bcc is set. */
    public void sendMimeMail(String to, String subject, String message) {
        send(to, subject, message, true);
    }

    /**
     * Sends one mail as multipart/alternative (plain text + HTML) from a named sender. Mails that are
     * HTML-only, CC'd to a third address or sent without a sender name are much more likely to land in spam.
     * Each mail gets a unique X-Entity-Ref-ID so Gmail doesn't fold a new OTP into an older conversation.
     * Temporary SMTP failures are retried a few times before giving up.
     */
    private void send(String to, String subject, String html, boolean archiveCopy) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= SEND_ATTEMPTS; attempt++) {
            try {
                if (brevoApiKey != null && !brevoApiKey.isBlank()) {
                    sendViaBrevo(to, subject, html, archiveCopy);
                    log.info("Mail sent successfully via Brevo to: {}", maskEmail(to));
                    return;
                }
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
                helper.setTo(to);
                helper.setFrom(this.from, this.fromName);
                if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
                if (archiveCopy && bcc != null && !bcc.isBlank() && !bcc.equalsIgnoreCase(to)) helper.setBcc(bcc);
                helper.setSubject(subject);
                helper.setText(toPlainText(html), html);
                mimeMessage.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString());
                mimeMessage.setHeader("Auto-Submitted", "auto-generated");

                mailSender.send(mimeMessage);
                log.info("Mail sent successfully to: {}", maskEmail(to));
                return;
            } catch (Exception e) {
                last = new RuntimeException("Failed to send email to " + maskEmail(to), e);
                log.warn("Attempt {} to send mail to {} failed: {}", attempt, maskEmail(to), e.getMessage());
                if (attempt < SEND_ATTEMPTS) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw last;
    }

    /** Sends one mail through Brevo's transactional email API (https://developers.brevo.com/reference/sendtransacemail). */
    private void sendViaBrevo(String to, String subject, String html, boolean archiveCopy) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("name", fromName, "email", from));
        body.put("to", List.of(Map.of("email", to)));
        if (replyTo != null && !replyTo.isBlank()) body.put("replyTo", Map.of("email", replyTo));
        if (archiveCopy && bcc != null && !bcc.isBlank() && !bcc.equalsIgnoreCase(to)) {
            body.put("bcc", List.of(Map.of("email", bcc)));
        }
        body.put("subject", subject);
        body.put("htmlContent", html);
        body.put("textContent", toPlainText(html));
        body.put("headers", Map.of("X-Entity-Ref-ID", UUID.randomUUID().toString()));

        brevo.post()
                .uri("/smtp/email")
                .header("api-key", brevoApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Readable plain-text version of an HTML mail body. */
    static String toPlainText(String html) {
        String text = html
                .replaceAll("(?is)<(head|style|title)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|tr|li)>", "\n")
                .replaceAll("(?i)</td>\\s*<td[^>]*>", ": ")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim().replaceAll("\\s{2,}", " ");
            if (!trimmed.isEmpty()) out.append(trimmed).append('\n');
        }
        return out.toString().trim();
    }

    public String maskEmail(String email) {
        try {
            int atIndex = email.indexOf("@");
            String name = email.substring(0, atIndex);
            String domain = email.substring(atIndex);

            if (name.length() <= 2) {
                return name.charAt(0) + "*" + domain;
            }

            StringBuilder masked = new StringBuilder();
            masked.append(name.charAt(0));

            for (int i = 1; i < name.length() - 1; i++) {
                masked.append("*");
            }

//          both do the same work
//        masked.append("*".repeat(name.length() - 2));

            masked.append(name.charAt(name.length() - 1));

            return masked.toString() + domain;
        } catch (Exception e) {
            log.warn("Exception occurred while masking the mail id, due to: {}",e.getMessage());
            return email;
        }
    }




}