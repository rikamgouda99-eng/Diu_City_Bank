package com.dcb.config;

import com.dcb.entity.UserEntity;
import com.dcb.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Creates the bank admin on startup from environment variables, so a fresh database
 * (e.g. on Render) has an admin who can log in. Does nothing if ADMIN_EMAIL or
 * ADMIN_PASSWORD is not set, or if a user with that email or bank ID already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Value("${app.admin.bank-id:DCBADMIN}")
    private String bankId;

    @Value("${app.admin.first-name:Bank}")
    private String firstName;

    @Value("${app.admin.last-name:Admin}")
    private String lastName;

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (userRepo.existsByMailId(email) || userRepo.existsByBankId(bankId)) {
            log.info("Admin {} already exists, skipping admin seeding", bankId);
            return;
        }
        UserEntity admin = UserEntity.builder()
                .bankId(bankId)
                .mailId(email)
                .firstName(firstName)
                .lastName(lastName)
                .password(passwordEncoder.encode(password))
                .role("ADMIN")
                .status("ACTIVE")
                .isActive(true)
                .isVerified(true)
                .isApproved(true)
                .createdDate(LocalDate.now())
                .updatedDate(LocalDate.now())
                .build();
        userRepo.save(admin);
        log.info("Admin {} created from environment variables", bankId);
    }
}
