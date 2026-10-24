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

                + "<p>Here is your otp to register ourself with DIU city bank.</p>"

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