package com.dcb.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${mail.cc}")
    private String cc;

    @Value("${mail.bcc}")
    private String bcc;

    @Value("${spring.mail.username}")
    private String from;



    public void sendOtpMail(String email, String otp) {
        if (email == null || otp == null) {
            throw new IllegalArgumentException("Email or OTP cannot be null");
        }
        log.info("Sending mail to email: {}",maskEmail(email));

        String subject = "Registration OTP - DIU CITY BANK";

        String message = "<html><body>"
                + "<h2>Registration OTP </h2>"

                + "<p>Dear User,</p>"

                + "<p>Here is your otp to register yourself with DIU city bank.</p>"

                + "<p>Your One-Time Password (OTP) is:</p>"
                + "<h1 style='color: #2e6c80;'>" + otp + "</h1>"

                + "<p><b>Note:</b> This OTP is valid for <b>10 minutes</b> only.</p>"

                + "<p>If you did not request this, please ignore this email.</p>"

                + "<br/>"
                + "<p>Best Regards,<br/>"
                + "<b>DIU CITY BANK</b></p>"

                + "</body></html>";

        sendMimeMail(email, subject, message);
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

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public void sendMail(String to, String subject, String message){

        try {
            SimpleMailMessage simpleMailMessage = new SimpleMailMessage();

            simpleMailMessage.setTo(to);
            simpleMailMessage.setFrom(this.from);
            simpleMailMessage.setCc(this.cc);
            simpleMailMessage.setBcc(this.bcc);
            simpleMailMessage.setSubject(subject);
            simpleMailMessage.setText(message);

            mailSender.send(simpleMailMessage);

            String mailId = maskEmail(to);
            log.info("Mail sent successfully to: {}", mailId);
        }catch (Exception e){
            log.warn("Exception while sending mail to mail Id: {}",maskEmail(to));
        }
    }

    public void sendMimeMail(String to, String subject, String message){
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setTo(to);
            helper.setFrom(this.from);
            helper.setCc(this.cc);
            helper.setSubject(subject);
            helper.setText(message, true); // true = HTML

            mailSender.send(mimeMessage);
            String mailId = maskEmail(to);
            log.info("Mail sent successfully to: {}", mailId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send admission email", e);
        }
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