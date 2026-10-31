package com.dcb.service;

import com.dcb.config.BankProperties;
import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.EmiScheduleEntity;
import com.dcb.entity.LoanEntity;
import com.dcb.entity.TransactionStatusEntity;
import com.dcb.enums.EmiStatus;
import com.dcb.enums.LoanStatus;
import com.dcb.enums.LoanType;
import com.dcb.enums.TransactionType;
import com.dcb.exception.BankException;
import com.dcb.exception.NotFoundException;
import com.dcb.model.*;
import com.dcb.repository.EmiScheduleRepo;
import com.dcb.repository.LoanRepo;
import com.dcb.repository.UserRepo;
import com.dcb.service.calc.EmiCalculator;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Loan and EMI simulation for academic use. The calculator and the approval flow are
 * not a real credit underwriting system.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoanService {

    public static final String DISCLAIMER = "DEMO / SIMULATION: estimated figures for an academic project, not a loan offer or credit decision.";

    private final LoanRepo loanRepo;
    private final EmiScheduleRepo emiRepo;
    private final UserRepo userRepo;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final FinancialExecutor executor;
    private final BankProperties props;

    // ------------------------------------------------------------------ calculator

    public Map<String, Object> rates() {
        BankProperties.Loan loan = props.getLoan();
        return Map.of(
                "rates", loan.getRates(),
                "minAmount", loan.getMinAmount(),
                "maxAmount", loan.getMaxAmount(),
                "minTenureMonths", loan.getMinTenureMonths(),
                "maxTenureMonths", loan.getMaxTenureMonths(),
                "minRate", loan.getMinRate(),
                "maxRate", loan.getMaxRate(),
                "disclaimer", DISCLAIMER);
    }

    public BigDecimal rateFor(LoanType type) {
        BigDecimal rate = props.getLoan().getRates().get(type);
        if (rate == null) {
            throw new BankException("No interest rate configured for " + type + " loans.");
        }
        return rate;
    }

    public EmiCalculationResponse calculate(EmiCalculationRequest request) {
        validateAmountAndTenure(request.getAmount(), request.getTenureMonths());
        BigDecimal rate = request.getAnnualInterestRate() == null ? rateFor(request.getLoanType()) : request.getAnnualInterestRate();
        validateRate(rate);
        EmiCalculator.Plan plan = EmiCalculator.plan(request.getAmount(), rate, request.getTenureMonths(), LocalDate.now().plusMonths(1));
        return EmiCalculationResponse.builder()
                .loanType(request.getLoanType().name())
                .loanAmount(request.getAmount())
                .annualInterestRate(rate)
                .tenureMonths(request.getTenureMonths())
                .monthlyEmi(plan.emi())
                .totalInterest(plan.totalInterest())
                .totalRepayment(plan.totalRepayment())
                .disclaimer(DISCLAIMER)
                .build();
    }

    private void validateAmountAndTenure(BigDecimal amount, Integer tenure) {
        BankProperties.Loan loan = props.getLoan();
        if (amount == null || amount.compareTo(loan.getMinAmount()) < 0 || amount.compareTo(loan.getMaxAmount()) > 0) {
            throw new BankException("Loan amount must be between " + loan.getMinAmount().toPlainString() + " and " + loan.getMaxAmount().toPlainString() + ".");
        }
        if (tenure == null || tenure < loan.getMinTenureMonths() || tenure > loan.getMaxTenureMonths()) {
            throw new BankException("Loan tenure must be between " + loan.getMinTenureMonths() + " and " + loan.getMaxTenureMonths() + " months.");
        }
    }

    private void validateRate(BigDecimal rate) {
        if (rate.compareTo(props.getLoan().getMinRate()) < 0 || rate.compareTo(props.getLoan().getMaxRate()) > 0) {
            throw new BankException("Interest rate must be between " + props.getLoan().getMinRate().toPlainString()
                    + "% and " + props.getLoan().getMaxRate().toPlainString() + "%.");
        }
    }

    // ------------------------------------------------------------------ customer

    @Transactional
    public LoanResponse apply(String bankId, LoanApplyRequest request) {
        validateAmountAndTenure(request.getAmount(), request.getTenureMonths());
        accountService.assertUserActive(accountService.getUser(bankId));
        BankAccountEntity account = accountService.getAccount(bankId);
        accountService.assertActive(account, "Your account");
        if (loanRepo.countByBankIdAndStatus(bankId, LoanStatus.PENDING) > 0) {
            throw new BankException("You already have a loan application under review.");
        }
        LoanEntity loan = loanRepo.save(LoanEntity.builder()
                .loanId(Util.generateReference("LN").substring(0, 20))
                .accountNo(account.getAccountNo())
                .bankId(bankId)
                .loanType(request.getLoanType())
                .status(LoanStatus.PENDING)
                .requestedAmount(request.getAmount())
                .tenureMonths(request.getTenureMonths())
                .interestRate(rateFor(request.getLoanType()))
                .employmentType(request.getEmploymentType())
                .employerName(Util.blankToNull(request.getEmployerName()))
                .monthlyIncome(request.getMonthlyIncome())
                .purpose(Util.blankToNull(request.getPurpose()))
                .appliedAt(LocalDateTime.now())
                .build());
        log.info("{} applied for {} loan {} of {}", bankId, request.getLoanType(), loan.getLoanId(), request.getAmount());
        return toResponse(loan, null);
    }

    public List<LoanResponse> list(String bankId) {
        return loanRepo.findByBankIdOrderByAppliedAtDesc(bankId).stream().map(l -> toResponse(l, null)).toList();
    }

    public LoanResponse get(String bankId, String loanId) {
        return toResponse(own(bankId, loanId), null);
    }

    public List<EmiResponse> schedule(String bankId, String loanId) {
        own(bankId, loanId);
        return emiRepo.findByLoanIdOrderByEmiNumberAsc(loanId).stream().map(LoanService::toEmi).toList();
    }

    public List<EmiResponse> emiHistory(String bankId, String loanId) {
        own(bankId, loanId);
        return emiRepo.findByLoanIdAndStatusOrderByEmiNumberAsc(loanId, EmiStatus.PAID).stream().map(LoanService::toEmi).toList();
    }

    /** Pays the earliest unpaid EMI from the savings account. */
    public EmiResponse payEmi(String bankId, String loanId, EmiPaymentRequest request, String idempotencyKey) {
        idempotencyService.validateKey(idempotencyKey);
        own(bankId, loanId);
        return executor.run(() -> {
            BankAccountEntity account = accountService.lockOperableAccount(bankId);
            LoanEntity loan = loanRepo.lockById(loanId).orElseThrow();

            var prior = idempotencyService.findResult(bankId, idempotencyKey, "EMI_PAYMENT");
            if (prior.isPresent()) {
                EmiResponse replay = toEmi(emiRepo.findById(Long.valueOf(prior.get())).orElseThrow());
                replay.setReplayed(true);
                return replay;
            }
            if (loan.getStatus() != LoanStatus.ACTIVE) {
                throw new BankException("EMIs can only be paid on an active loan. This loan is " + loan.getStatus().name().toLowerCase() + ".");
            }
            EmiScheduleEntity emi = emiRepo.findFirstByLoanIdAndStatusOrderByEmiNumberAsc(loanId, EmiStatus.PENDING)
                    .orElseThrow(() -> new BankException("All EMIs are already paid."));
            Integer wanted = request == null ? null : request.getEmiNumber();
            if (wanted != null && !wanted.equals(emi.getEmiNumber())) {
                throw new BankException(wanted < emi.getEmiNumber()
                        ? "EMI #" + wanted + " is already paid."
                        : "Please pay EMI #" + emi.getEmiNumber() + " first.");
            }

            TransactionStatusEntity debit = ledgerService.debit(account, LedgerService.Posting.builder()
                    .type(TransactionType.EMI_PAYMENT)
                    .amount(emi.getEmiAmount())
                    .reference(Util.generateReference("EMI"))
                    .description("EMI #" + emi.getEmiNumber() + " for loan " + loanId)
                    .fromBankId(bankId)
                    .channel("LOAN_SIM")
                    .build());
            emi.setStatus(EmiStatus.PAID);
            emi.setPaymentDate(LocalDateTime.now());
            emi.setTransactionId(debit.getTransactionId());
            emiRepo.save(emi);

            loan.setOutstandingPrincipal(emi.getRemainingPrincipal());
            loan.setUpdatedAt(LocalDateTime.now());
            if (emiRepo.findFirstByLoanIdAndStatusOrderByEmiNumberAsc(loanId, EmiStatus.PENDING).isEmpty()) {
                loan.setStatus(LoanStatus.CLOSED);
                loan.setClosedAt(LocalDateTime.now());
                log.info("Loan {} fully repaid and closed", loanId);
            }
            loanRepo.save(loan);
            idempotencyService.remember(bankId, idempotencyKey, "EMI_PAYMENT", String.valueOf(emi.getId()));
            return toEmi(emi);
        });
    }

    // ------------------------------------------------------------------ admin

    public PageResponse<LoanResponse> adminList(LoanStatus status, int page, int size) {
        return PageResponse.of(loanRepo.search(status, Util.page(page, size)), l -> toResponse(l, customerName(l.getBankId())));
    }

    public LoanResponse adminGet(String loanId) {
        LoanEntity loan = find(loanId);
        return toResponse(loan, customerName(loan.getBankId()));
    }

    public List<EmiResponse> adminSchedule(String loanId) {
        find(loanId);
        return emiRepo.findByLoanIdOrderByEmiNumberAsc(loanId).stream().map(LoanService::toEmi).toList();
    }

    public List<EmiResponse> adminEmiHistory(String loanId) {
        find(loanId);
        return emiRepo.findByLoanIdAndStatusOrderByEmiNumberAsc(loanId, EmiStatus.PAID).stream().map(LoanService::toEmi).toList();
    }

    public PageResponse<EmiResponse> adminAllEmiPayments(int page, int size) {
        return PageResponse.of(emiRepo.findByStatusOrderByPaymentDateDesc(EmiStatus.PAID, Util.page(page, size)), LoanService::toEmi);
    }

    /** Approves the loan, builds the EMI schedule and disburses the amount to the savings account. */
    public LoanResponse approve(String loanId, LoanApprovalRequest request) {
        LoanEntity peek = find(loanId);
        return executor.run(() -> {
            BankAccountEntity account = accountService.lockAccount(peek.getAccountNo());
            accountService.assertActive(account, "Customer account");
            LoanEntity loan = loanRepo.lockById(loanId).orElseThrow();
            if (loan.getStatus() != LoanStatus.PENDING) {
                throw new BankException("Loan is already " + loan.getStatus().name().toLowerCase() + ".");
            }
            BigDecimal amount = request == null || request.getApprovedAmount() == null ? loan.getRequestedAmount() : request.getApprovedAmount();
            if (amount.compareTo(loan.getRequestedAmount()) > 0) {
                throw new BankException("Approved amount can't be more than the requested amount.");
            }
            validateAmountAndTenure(amount, loan.getTenureMonths());
            BigDecimal rate = request == null || request.getInterestRate() == null ? rateFor(loan.getLoanType()) : request.getInterestRate();
            validateRate(rate);

            LocalDate firstDue = LocalDate.now().plusMonths(1);
            EmiCalculator.Plan plan = EmiCalculator.plan(amount, rate, loan.getTenureMonths(), firstDue);
            for (EmiCalculator.Instalment row : plan.schedule()) {
                emiRepo.save(EmiScheduleEntity.builder()
                        .loanId(loanId)
                        .emiNumber(row.number())
                        .dueDate(row.dueDate())
                        .principalComponent(row.principal())
                        .interestComponent(row.interest())
                        .emiAmount(row.emi())
                        .remainingPrincipal(row.remainingPrincipal())
                        .status(EmiStatus.PENDING)
                        .build());
            }
            TransactionStatusEntity credit = ledgerService.credit(account, LedgerService.Posting.builder()
                    .type(TransactionType.LOAN_DISBURSAL)
                    .amount(amount)
                    .reference(loanId)
                    .description(loan.getLoanType() + " loan " + loanId + " disbursed (simulated)")
                    .toBankId(loan.getBankId())
                    .channel("LOAN_SIM")
                    .build());

            loan.setApprovedAmount(amount);
            loan.setInterestRate(rate);
            loan.setEmiAmount(plan.emi());
            loan.setTotalInterest(plan.totalInterest());
            loan.setTotalRepayment(plan.totalRepayment());
            loan.setOutstandingPrincipal(amount);
            loan.setFirstEmiDate(firstDue);
            loan.setDisbursalTransactionId(credit.getTransactionId());
            loan.setStatus(LoanStatus.ACTIVE);
            loan.setAdminComment(request == null ? null : Util.blankToNull(request.getComment()));
            loan.setDecidedAt(LocalDateTime.now());
            loan.setUpdatedAt(LocalDateTime.now());
            loanRepo.save(loan);
            log.info("Loan {} approved: {} @ {}% for {} months, EMI {}", loanId, amount, rate, loan.getTenureMonths(), plan.emi());
            return toResponse(loan, customerName(loan.getBankId()));
        });
    }

    @Transactional
    public LoanResponse reject(String loanId, DecisionRequest request) {
        String comment = Util.blankToNull(request == null ? null : request.getComment());
        if (comment == null) {
            throw new BankException("Please give a reason for rejecting the loan.");
        }
        LoanEntity loan = loanRepo.lockById(loanId).orElseThrow(() -> new NotFoundException("Loan not found."));
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new BankException("Loan is already " + loan.getStatus().name().toLowerCase() + ".");
        }
        loan.setStatus(LoanStatus.REJECTED);
        loan.setAdminComment(comment);
        loan.setDecidedAt(LocalDateTime.now());
        loan.setUpdatedAt(LocalDateTime.now());
        loanRepo.save(loan);
        return toResponse(loan, customerName(loan.getBankId()));
    }

    // ------------------------------------------------------------------ helpers

    private LoanEntity find(String loanId) {
        return loanRepo.findById(loanId).orElseThrow(() -> new NotFoundException("Loan not found."));
    }

    private LoanEntity own(String bankId, String loanId) {
        return loanRepo.findByLoanIdAndBankId(loanId, bankId).orElseThrow(() -> new NotFoundException("Loan not found."));
    }

    private String customerName(String bankId) {
        return userRepo.findById(bankId).map(Util::fullName).orElse(null);
    }

    private LoanResponse toResponse(LoanEntity l, String customerName) {
        LoanResponse.LoanResponseBuilder b = LoanResponse.builder()
                .loanId(l.getLoanId())
                .bankId(l.getBankId())
                .customerName(customerName)
                .accountNo(l.getAccountNo())
                .loanType(l.getLoanType().name())
                .status(l.getStatus().name())
                .requestedAmount(l.getRequestedAmount())
                .approvedAmount(l.getApprovedAmount())
                .interestRate(l.getInterestRate())
                .tenureMonths(l.getTenureMonths())
                .monthlyEmi(l.getEmiAmount())
                .totalInterest(l.getTotalInterest())
                .totalRepayment(l.getTotalRepayment())
                .outstandingPrincipal(l.getOutstandingPrincipal())
                .employmentType(l.getEmploymentType())
                .employerName(l.getEmployerName())
                .monthlyIncome(l.getMonthlyIncome())
                .purpose(l.getPurpose())
                .adminComment(l.getAdminComment())
                .disbursalTransactionId(l.getDisbursalTransactionId())
                .appliedAt(l.getAppliedAt())
                .decidedAt(l.getDecidedAt())
                .closedAt(l.getClosedAt())
                .simulated(true);
        if (l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.CLOSED) {
            List<EmiScheduleEntity> emis = emiRepo.findByLoanIdOrderByEmiNumberAsc(l.getLoanId());
            int paid = (int) emis.stream().filter(e -> e.getStatus() == EmiStatus.PAID).count();
            b.emisPaid(paid).emisPending(emis.size() - paid);
            emis.stream().filter(e -> e.getStatus() == EmiStatus.PENDING).findFirst()
                    .ifPresent(next -> b.nextEmiDueDate(next.getDueDate()).nextEmiAmount(next.getEmiAmount()));
        }
        return b.build();
    }

    private static EmiResponse toEmi(EmiScheduleEntity e) {
        return EmiResponse.builder()
                .id(e.getId())
                .loanId(e.getLoanId())
                .emiNumber(e.getEmiNumber())
                .dueDate(e.getDueDate())
                .principalComponent(e.getPrincipalComponent())
                .interestComponent(e.getInterestComponent())
                .emiAmount(e.getEmiAmount())
                .remainingPrincipal(e.getRemainingPrincipal())
                .status(e.getStatus().name())
                .paymentDate(e.getPaymentDate())
                .transactionId(e.getTransactionId())
                .build();
    }
}
