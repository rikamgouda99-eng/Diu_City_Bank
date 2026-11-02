package com.dcb.service;

import com.dcb.entity.UserEntity;
import com.dcb.repository.UserRepo;
import com.dcb.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends a confirmation mail to the customer for every transaction or account action.
 * Mails go out on a background thread, so a slow or failing mail server never blocks
 * or rolls back the banking operation itself.
 */
@Service
@Slf4j
public class NotificationService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final UserRepo userRepo;
    private final MailService mailService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public NotificationService(UserRepo userRepo, MailService mailService) {
        this.userRepo = userRepo;
        this.mailService = mailService;
    }

    /** Builder for the "label: value" rows shown in the mail, in insertion order. */
    public static Details details() {
        return new Details();
    }

    public void notify(String bankId, String subject, String headline, Details details) {
        if (bankId == null) return;
        try {
            UserEntity user = userRepo.findById(bankId).orElse(null);
            if (user == null || user.getMailId() == null || user.getMailId().isBlank()) return;
            String name = Util.fullName(user);
            Map<String, String> rows = new LinkedHashMap<>(details == null ? Map.of() : details.rows);
            rows.putIfAbsent("Date & time", LocalDateTime.now().format(WHEN));
            executor.submit(() -> {
                try {
                    mailService.sendNotificationMail(user.getMailId(), name, subject, headline, rows);
                } catch (Exception e) {
                    log.warn("{} could not send '{}' mail: {}", bankId, subject, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("{} could not queue '{}' mail: {}", bankId, subject, e.getMessage());
        }
    }

    public static String money(BigDecimal amount) {
        return amount == null ? "-" : "₹ " + String.format("%,.2f", amount);
    }

    public static final class Details {
        private final Map<String, String> rows = new LinkedHashMap<>();

        public Details add(String label, Object value) {
            if (value != null && !value.toString().isBlank()) rows.put(label, value.toString());
            return this;
        }

        public Details amount(String label, BigDecimal value) {
            return value == null ? this : add(label, money(value));
        }
    }
}
