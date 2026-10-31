package com.dcb.service;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.UserEntity;
import com.dcb.enums.*;
import com.dcb.exception.NotFoundException;
import com.dcb.model.*;
import com.dcb.repository.*;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Admin dashboard figures plus customer and account management. */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminBankingService {

    private final UserRepo userRepo;
    private final BankAccountRepo accountRepo;
    private final TransactionRepo txnRepo;
    private final FixedDepositRepo fdRepo;
    private final CardRepo cardRepo;
    private final LoanRepo loanRepo;
    private final KycRequestRepo kycRepo;
    private final CustomerRequestRepo requestRepo;
    private final KycService kycService;

    public AdminDashboardResponse dashboard() {
        return AdminDashboardResponse.builder()
                .totalCustomers(userRepo.countByRole("CUSTOMER"))
                .pendingApprovals((long) userRepo.getPendingUsers().size())
                .totalAccounts(accountRepo.count())
                .totalDeposits(accountRepo.totalBalance())
                .transactionsToday(txnRepo.countByCreatedAtAfter(LocalDate.now().atStartOfDay()))
                .failedTransactions(txnRepo.countByStatus(TransactionStatus.FAILED))
                .pendingFds(fdRepo.countByStatus(FdStatus.PENDING))
                .activeFds(fdRepo.countByStatus(FdStatus.ACTIVE))
                .pendingCardApplications(cardRepo.countByStatus(CardStatus.APPLIED))
                .activeCards(cardRepo.countByStatus(CardStatus.ACTIVE))
                .pendingLoans(loanRepo.countByStatus(LoanStatus.PENDING))
                .activeLoans(loanRepo.countByStatus(LoanStatus.ACTIVE))
                .loanOutstanding(loanRepo.totalOutstanding())
                .pendingKyc(kycRepo.countByStatus(KycStatus.PENDING))
                .openRequests(requestRepo.countByStatus(RequestStatus.OPEN))
                .build();
    }

    public PageResponse<CustomerSummaryResponse> customers(String search, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdDate"));
        return PageResponse.of(userRepo.searchCustomers(Util.blankToNull(search), pageable), this::toCustomer);
    }

    public CustomerSummaryResponse customer(String bankId) {
        UserEntity user = userRepo.findById(bankId).orElseThrow(() -> new NotFoundException("Customer not found."));
        return toCustomer(user);
    }

    public PageResponse<AccountAdminResponse> accounts(String search, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("accountNo"));
        return PageResponse.of(accountRepo.search(Util.blankToNull(search), pageable), this::toAccount);
    }

    @Transactional
    public AccountAdminResponse setAccountStatus(String accountNo, AccountStatusRequest request) {
        BankAccountEntity account = accountRepo.lockByAccountNo(accountNo).orElseThrow(() -> new NotFoundException("Account not found."));
        account.setStatus(request.getStatus());
        account.setStatusReason(Util.blankToNull(request.getReason()));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepo.save(account);
        log.info("Account {} status set to {}", accountNo, request.getStatus());
        return toAccount(account);
    }

    private CustomerSummaryResponse toCustomer(UserEntity u) {
        BankAccountEntity account = accountRepo.findByCustomerId(u.getBankId()).orElse(null);
        return CustomerSummaryResponse.builder()
                .bankId(u.getBankId())
                .name(Util.fullName(u))
                .mailId(u.getMailId())
                .phoneNumber(u.getPhoneNumber())
                .status(u.getStatus())
                .isActive(u.getIsActive())
                .isVerified(u.getIsVerified())
                .isApproved(u.getIsApproved())
                .accountNo(account == null ? null : account.getAccountNo())
                .balance(account == null ? null : account.getAccountBalance())
                .accountStatus(account == null ? null : account.effectiveStatus().name())
                .kycStatus(kycService.statusOf(u.getBankId()).name())
                .createdDate(u.getCreatedDate())
                .build();
    }

    private AccountAdminResponse toAccount(BankAccountEntity a) {
        return AccountAdminResponse.builder()
                .accountNo(a.getAccountNo())
                .bankId(a.getCustomerId())
                .holderName(userRepo.findById(a.getCustomerId()).map(Util::fullName).orElse(null))
                .accountType(a.getAccountType() == null ? "SAVINGS" : a.getAccountType())
                .balance(a.getAccountBalance())
                .status(a.effectiveStatus().name())
                .statusReason(a.getStatusReason())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
