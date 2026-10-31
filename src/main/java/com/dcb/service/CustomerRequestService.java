package com.dcb.service;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.CustomerRequestEntity;
import com.dcb.entity.UserEntity;
import com.dcb.enums.AccountStatus;
import com.dcb.enums.LoanStatus;
import com.dcb.enums.RequestStatus;
import com.dcb.enums.RequestType;
import com.dcb.exception.BankException;
import com.dcb.exception.NotFoundException;
import com.dcb.model.*;
import com.dcb.repository.BankAccountRepo;
import com.dcb.repository.CustomerRequestRepo;
import com.dcb.repository.LoanRepo;
import com.dcb.repository.UserRepo;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Service requests raised by customers (cheque book, account closure...) and reviewed by the admin. */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerRequestService {

    private final CustomerRequestRepo requestRepo;
    private final UserRepo userRepo;
    private final BankAccountRepo accountRepo;
    private final LoanRepo loanRepo;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CustomerRequestResponse create(String bankId, CustomerRequestCreate request) {
        if (request.getRequestType() == RequestType.ACCOUNT_CLOSURE) {
            throw new BankException("Use the account closure option to close your account.");
        }
        accountService.getUser(bankId);
        if (requestRepo.existsByBankIdAndRequestTypeAndStatus(bankId, request.getRequestType(), RequestStatus.OPEN)) {
            throw new BankException("You already have an open " + label(request.getRequestType()) + " request.");
        }
        return toResponse(save(bankId, request.getRequestType(), request.getSubject().trim(), Util.blankToNull(request.getDescription())), null);
    }

    @Transactional
    public CustomerRequestResponse requestAccountClosure(String bankId, AccountClosureRequest request) {
        UserEntity user = accountService.getUser(bankId);
        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BankException("Password is incorrect.");
        }
        BankAccountEntity account = accountService.getAccount(bankId);
        if (account.effectiveStatus() == AccountStatus.CLOSED) {
            throw new BankException("Your account is already closed.");
        }
        if (requestRepo.existsByBankIdAndRequestTypeAndStatus(bankId, RequestType.ACCOUNT_CLOSURE, RequestStatus.OPEN)) {
            throw new BankException("An account closure request is already under review.");
        }
        if (loanRepo.countByBankIdAndStatus(bankId, LoanStatus.ACTIVE) > 0) {
            throw new BankException("Please repay your active loans before closing the account.");
        }
        CustomerRequestEntity saved = save(bankId, RequestType.ACCOUNT_CLOSURE, "Account closure - " + account.getAccountNo(), request.getReason().trim());
        log.info("{} requested closure of account {}", bankId, account.getAccountNo());
        return toResponse(saved, null);
    }

    public List<CustomerRequestResponse> mine(String bankId) {
        return requestRepo.findByBankIdOrderByCreatedAtDesc(bankId).stream().map(r -> toResponse(r, null)).toList();
    }

    public PageResponse<CustomerRequestResponse> adminList(RequestStatus status, int page, int size) {
        return PageResponse.of(requestRepo.search(status, Util.page(page, size)),
                r -> toResponse(r, userRepo.findById(r.getBankId()).map(Util::fullName).orElse(null)));
    }

    /** Approving an ACCOUNT_CLOSURE request closes the account; other types are just marked done. */
    @Transactional
    public CustomerRequestResponse decide(Long id, boolean approve, DecisionRequest decision) {
        CustomerRequestEntity request = requestRepo.findById(id).orElseThrow(() -> new NotFoundException("Request not found."));
        if (request.getStatus() != RequestStatus.OPEN) {
            throw new BankException("Request is already " + request.getStatus().name().toLowerCase() + ".");
        }
        String comment = Util.blankToNull(decision == null ? null : decision.getComment());
        if (!approve && comment == null) {
            throw new BankException("Please give a reason for rejecting the request.");
        }
        if (approve && request.getRequestType() == RequestType.ACCOUNT_CLOSURE) {
            BankAccountEntity account = accountRepo.lockByCustomerId(request.getBankId())
                    .orElseThrow(() -> new BankException("Customer has no bank account."));
            account.setStatus(AccountStatus.CLOSED);
            account.setStatusReason("Closed on customer request #" + id);
            account.setUpdatedAt(LocalDateTime.now());
            accountRepo.save(account);
            log.info("Account {} closed on request {}", account.getAccountNo(), id);
        }
        request.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        request.setAdminComment(comment);
        request.setUpdatedAt(LocalDateTime.now());
        requestRepo.save(request);
        return toResponse(request, userRepo.findById(request.getBankId()).map(Util::fullName).orElse(null));
    }

    private CustomerRequestEntity save(String bankId, RequestType type, String subject, String description) {
        return requestRepo.save(CustomerRequestEntity.builder()
                .bankId(bankId)
                .requestType(type)
                .subject(subject)
                .description(description)
                .status(RequestStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private static String label(RequestType type) {
        return type.name().toLowerCase().replace('_', ' ');
    }

    private CustomerRequestResponse toResponse(CustomerRequestEntity r, String customerName) {
        return CustomerRequestResponse.builder()
                .id(r.getId())
                .bankId(r.getBankId())
                .customerName(customerName)
                .requestType(r.getRequestType().name())
                .subject(r.getSubject())
                .description(r.getDescription())
                .status(r.getStatus().name())
                .adminComment(r.getAdminComment())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
