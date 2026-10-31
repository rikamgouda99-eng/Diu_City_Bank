package com.dcb.service;

import com.dcb.config.BankProperties;
import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.FixedDepositEntity;
import com.dcb.entity.TransactionStatusEntity;
import com.dcb.enums.FdClosureType;
import com.dcb.enums.FdStatus;
import com.dcb.enums.TransactionStatus;
import com.dcb.enums.TransactionType;
import com.dcb.exception.BankException;
import com.dcb.exception.NotFoundException;
import com.dcb.model.*;
import com.dcb.repository.FixedDepositRepo;
import com.dcb.repository.TransactionRepo;
import com.dcb.repository.UserRepo;
import com.dcb.service.calc.InterestCalculator;
import com.dcb.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Simulated fixed deposits. The principal leaves the savings account when the FD is booked
 * (status PENDING); the admin approves it (ACTIVE) or rejects it (principal refunded).
 * On maturity the maturity amount is credited exactly once.
 */
@Service
@Slf4j
public class FixedDepositService {

    private final FixedDepositRepo fdRepo;
    private final TransactionRepo txnRepo;
    private final UserRepo userRepo;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final FinancialExecutor executor;
    private final BankProperties props;
    private final TransactionTemplate tx;

    public FixedDepositService(FixedDepositRepo fdRepo, TransactionRepo txnRepo, UserRepo userRepo,
                               AccountService accountService, LedgerService ledgerService,
                               IdempotencyService idempotencyService, FinancialExecutor executor,
                               BankProperties props, PlatformTransactionManager transactionManager) {
        this.fdRepo = fdRepo;
        this.txnRepo = txnRepo;
        this.userRepo = userRepo;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.executor = executor;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ rates & quotes

    public Map<String, Object> rates() {
        BankProperties.Fd fd = props.getFd();
        return Map.of(
                "slabs", fd.getRates(),
                "compoundingPerYear", fd.getCompoundingPerYear(),
                "minAmount", fd.getMinAmount(),
                "maxAmount", fd.getMaxAmount(),
                "minTenureMonths", fd.getMinTenureMonths(),
                "maxTenureMonths", fd.getMaxTenureMonths(),
                "minRate", fd.getMinRate(),
                "maxRate", fd.getMaxRate(),
                "prematurePenaltyRate", fd.getPrematurePenaltyRate(),
                "prematureMinDays", fd.getPrematureMinDays());
    }

    public BigDecimal rateFor(int tenureMonths) {
        return props.getFd().getRates().stream()
                .filter(s -> tenureMonths <= s.getUpToMonths())
                .findFirst()
                .orElse(props.getFd().getRates().get(props.getFd().getRates().size() - 1))
                .getRate();
    }

    public FdQuoteResponse quote(FdCreateRequest request) {
        validate(request);
        BigDecimal rate = request.getInterestRate() == null ? rateFor(request.getTenureMonths()) : request.getInterestRate();
        return quote(request.getAmount(), rate, request.getTenureMonths(), LocalDate.now());
    }

    private FdQuoteResponse quote(BigDecimal principal, BigDecimal rate, int months, LocalDate start) {
        int n = props.getFd().getCompoundingPerYear();
        BigDecimal maturity = InterestCalculator.maturityAmount(principal, rate, months, n);
        return FdQuoteResponse.builder()
                .principal(principal)
                .interestRate(rate)
                .tenureMonths(months)
                .compoundingPerYear(n)
                .maturityAmount(maturity)
                .totalInterest(maturity.subtract(principal))
                .depositDate(start)
                .maturityDate(start.plusMonths(months))
                .formula("A = P x (1 + R / (100 x N)) ^ (N x T)")
                .build();
    }

    private void validate(FdCreateRequest request) {
        BankProperties.Fd fd = props.getFd();
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(fd.getMinAmount()) < 0) {
            throw new BankException("Minimum FD amount is " + fd.getMinAmount().toPlainString() + ".");
        }
        if (amount.compareTo(fd.getMaxAmount()) > 0) {
            throw new BankException("Maximum FD amount is " + fd.getMaxAmount().toPlainString() + ".");
        }
        if (request.getTenureMonths() == null || request.getTenureMonths() < fd.getMinTenureMonths()
                || request.getTenureMonths() > fd.getMaxTenureMonths()) {
            throw new BankException("FD tenure must be between " + fd.getMinTenureMonths() + " and " + fd.getMaxTenureMonths() + " months.");
        }
        validateRate(request.getInterestRate());
    }

    private void validateRate(BigDecimal rate) {
        if (rate != null && (rate.compareTo(props.getFd().getMinRate()) < 0 || rate.compareTo(props.getFd().getMaxRate()) > 0)) {
            throw new BankException("FD interest rate must be between " + props.getFd().getMinRate().toPlainString()
                    + "% and " + props.getFd().getMaxRate().toPlainString() + "%.");
        }
    }

    // ------------------------------------------------------------------ customer

    public FdResponse create(String bankId, FdCreateRequest request, String idempotencyKey) {
        validate(request);
        idempotencyService.validateKey(idempotencyKey);
        return executor.run(() -> {
            BankAccountEntity account = accountService.lockOperableAccount(bankId);
            var prior = idempotencyService.findResult(bankId, idempotencyKey, "FD_CREATE");
            if (prior.isPresent()) {
                FdResponse replay = toResponse(fdRepo.findById(prior.get()).orElseThrow(), null);
                replay.setReplayed(true);
                return replay;
            }

            BigDecimal rate = request.getInterestRate() == null ? rateFor(request.getTenureMonths()) : request.getInterestRate();
            FdQuoteResponse quote = quote(request.getAmount(), rate, request.getTenureMonths(), LocalDate.now());
            String fdId = Util.generateReference("FD").substring(0, 20);

            TransactionStatusEntity funding = ledgerService.debit(account, LedgerService.Posting.builder()
                    .type(TransactionType.FD_BOOKING)
                    .amount(request.getAmount())
                    .reference(fdId)
                    .description("Fixed deposit " + fdId + " booked for " + request.getTenureMonths() + " months @ " + rate.toPlainString() + "%")
                    .fromBankId(bankId)
                    .channel("FD_SIM")
                    .build());

            FixedDepositEntity fd = fdRepo.save(FixedDepositEntity.builder()
                    .fdId(fdId)
                    .accountNo(account.getAccountNo())
                    .bankId(bankId)
                    .principal(request.getAmount())
                    .interestRate(rate)
                    .tenureMonths(request.getTenureMonths())
                    .compoundingPerYear(quote.getCompoundingPerYear())
                    .depositDate(quote.getDepositDate())
                    .maturityDate(quote.getMaturityDate())
                    .maturityAmount(quote.getMaturityAmount())
                    .expectedInterest(quote.getTotalInterest())
                    .status(FdStatus.PENDING)
                    .fundingTransactionId(funding.getTransactionId())
                    .createdAt(LocalDateTime.now())
                    .build());
            account.setFixedDepositId(fdId);
            idempotencyService.remember(bankId, idempotencyKey, "FD_CREATE", fdId);
            log.info("{} booked FD {} of {} for {} months", bankId, fdId, request.getAmount(), request.getTenureMonths());
            return toResponse(fd, null);
        });
    }

    public List<FdResponse> list(String bankId) {
        return fdRepo.findByBankIdOrderByCreatedAtDesc(bankId).stream().map(f -> toResponse(f, null)).toList();
    }

    public FdResponse get(String bankId, String fdId) {
        return toResponse(own(bankId, fdId), null);
    }

    public FdQuoteResponse maturity(String bankId, String fdId) {
        FixedDepositEntity fd = own(bankId, fdId);
        return FdQuoteResponse.builder()
                .principal(fd.getPrincipal())
                .interestRate(fd.getInterestRate())
                .tenureMonths(fd.getTenureMonths())
                .compoundingPerYear(fd.getCompoundingPerYear())
                .totalInterest(fd.getExpectedInterest())
                .maturityAmount(fd.getMaturityAmount())
                .depositDate(fd.getDepositDate())
                .maturityDate(fd.getMaturityDate())
                .formula("A = P x (1 + R / (100 x N)) ^ (N x T)")
                .build();
    }

    public FdSummaryResponse summary(String bankId) {
        List<FixedDepositEntity> fds = fdRepo.findByBankIdOrderByCreatedAtDesc(bankId);
        List<FixedDepositEntity> live = fds.stream().filter(f -> f.getStatus() == FdStatus.ACTIVE || f.getStatus() == FdStatus.PENDING).toList();
        return FdSummaryResponse.builder()
                .totalFds((long) fds.size())
                .activeFds(count(fds, FdStatus.ACTIVE))
                .pendingFds(count(fds, FdStatus.PENDING))
                .maturedFds(count(fds, FdStatus.MATURED))
                .closedFds(count(fds, FdStatus.CLOSED))
                .rejectedFds(count(fds, FdStatus.REJECTED))
                .totalInvested(sum(live.stream().map(FixedDepositEntity::getPrincipal).toList()))
                .totalExpectedInterest(sum(live.stream().map(FixedDepositEntity::getExpectedInterest).toList()))
                .totalMaturityAmount(sum(live.stream().map(FixedDepositEntity::getMaturityAmount).toList()))
                .build();
    }

    public FdClosurePreviewResponse closePreview(String bankId, String fdId) {
        FixedDepositEntity fd = own(bankId, fdId);
        if (fd.getStatus() != FdStatus.ACTIVE) {
            throw new BankException("Only active FDs can be closed. This FD is " + fd.getStatus().name().toLowerCase() + ".");
        }
        return preview(fd, LocalDate.now());
    }

    public FdResponse close(String bankId, String fdId, String idempotencyKey) {
        idempotencyService.validateKey(idempotencyKey);
        own(bankId, fdId);
        return executor.run(() -> {
            BankAccountEntity account = accountService.lockOperableAccount(bankId);
            FixedDepositEntity fd = fdRepo.lockById(fdId).orElseThrow(() -> new NotFoundException("Fixed deposit not found."));
            var prior = idempotencyService.findResult(bankId, idempotencyKey, "FD_CLOSE");
            if (prior.isPresent()) {
                FdResponse replay = toResponse(fdRepo.findById(prior.get()).orElseThrow(), null);
                replay.setReplayed(true);
                return replay;
            }
            if (fd.getStatus() != FdStatus.ACTIVE) {
                throw new BankException("Only active FDs can be closed. This FD is " + fd.getStatus().name().toLowerCase() + ".");
            }
            payout(fd, account, LocalDate.now());
            idempotencyService.remember(bankId, idempotencyKey, "FD_CLOSE", fdId);
            return toResponse(fd, null);
        });
    }

    // ------------------------------------------------------------------ maturity

    /** Daily job (and admin trigger): pays out every ACTIVE FD whose maturity date has arrived. */
    @Scheduled(cron = "${bank.fd.maturity-cron:0 5 0 * * *}")
    public void scheduledMaturity() {
        int paid = processMaturities();
        if (paid > 0) log.info("Maturity job paid out {} fixed deposits", paid);
    }

    public int processMaturities() {
        LocalDate today = LocalDate.now();
        int paid = 0;
        for (String fdId : fdRepo.findMaturedIds(today)) {
            try {
                Boolean done = tx.execute(status -> {
                    FixedDepositEntity peek = fdRepo.findById(fdId).orElseThrow();
                    BankAccountEntity account = accountService.lockAccount(peek.getAccountNo());
                    FixedDepositEntity fd = fdRepo.lockById(fdId).orElseThrow();
                    // re-checked under the lock: a concurrent close or an earlier run may have paid it already
                    if (fd.getStatus() != FdStatus.ACTIVE || fd.getPayoutTransactionId() != null || fd.getMaturityDate().isAfter(today)) {
                        return false;
                    }
                    payout(fd, account, today);
                    return true;
                });
                if (Boolean.TRUE.equals(done)) paid++;
            } catch (RuntimeException e) {
                log.warn("Maturity payout failed for FD {}: {}", fdId, e.getMessage());
            }
        }
        return paid;
    }

    /** Caller holds locks on the account and the FD and has checked status ACTIVE. */
    private void payout(FixedDepositEntity fd, BankAccountEntity account, LocalDate on) {
        FdClosurePreviewResponse calc = preview(fd, on);
        boolean matured = FdClosureType.MATURITY.name().equals(calc.getClosureType());
        TransactionStatusEntity credit = ledgerService.credit(account, LedgerService.Posting.builder()
                .type(TransactionType.FD_PAYOUT)
                .amount(calc.getPayoutAmount())
                .reference(fd.getFdId())
                .description((matured ? "FD maturity payout " : "FD premature closure ") + fd.getFdId())
                .toBankId(fd.getBankId())
                .channel("FD_SIM")
                .build());
        fd.setStatus(matured ? FdStatus.MATURED : FdStatus.CLOSED);
        fd.setClosureType(matured ? FdClosureType.MATURITY : FdClosureType.PREMATURE);
        fd.setPenaltyRate(calc.getPenaltyRate());
        fd.setPayoutAmount(calc.getPayoutAmount());
        fd.setPayoutTransactionId(credit.getTransactionId());
        fd.setClosedAt(LocalDateTime.now());
        fd.setUpdatedAt(LocalDateTime.now());
        fdRepo.save(fd);
        log.info("FD {} {} with payout {}", fd.getFdId(), fd.getStatus(), calc.getPayoutAmount());
    }

    private FdClosurePreviewResponse preview(FixedDepositEntity fd, LocalDate on) {
        long daysHeld = Math.max(ChronoUnit.DAYS.between(fd.getDepositDate(), on), 0);
        if (!on.isBefore(fd.getMaturityDate())) {
            return FdClosurePreviewResponse.builder()
                    .fdId(fd.getFdId())
                    .closureType(FdClosureType.MATURITY.name())
                    .principal(fd.getPrincipal())
                    .contractedRate(fd.getInterestRate())
                    .penaltyRate(BigDecimal.ZERO)
                    .applicableRate(fd.getInterestRate())
                    .daysHeld(daysHeld)
                    .interestEarned(fd.getExpectedInterest())
                    .payoutAmount(fd.getMaturityAmount())
                    .maturityDate(fd.getMaturityDate())
                    .maturityAmountIfHeld(fd.getMaturityAmount())
                    .note("FD has matured, the full maturity amount will be credited.")
                    .build();
        }
        BigDecimal penalty = props.getFd().getPrematurePenaltyRate();
        BigDecimal applicable = fd.getInterestRate().subtract(penalty).max(BigDecimal.ZERO);
        boolean tooEarly = daysHeld < props.getFd().getPrematureMinDays();
        BigDecimal payout = tooEarly ? fd.getPrincipal().setScale(2, RoundingMode.HALF_UP)
                : InterestCalculator.maturityAmountForDays(fd.getPrincipal(), applicable, daysHeld, fd.getCompoundingPerYear());
        return FdClosurePreviewResponse.builder()
                .fdId(fd.getFdId())
                .closureType(FdClosureType.PREMATURE.name())
                .principal(fd.getPrincipal())
                .contractedRate(fd.getInterestRate())
                .penaltyRate(penalty)
                .applicableRate(tooEarly ? BigDecimal.ZERO : applicable)
                .daysHeld(daysHeld)
                .interestEarned(payout.subtract(fd.getPrincipal()))
                .payoutAmount(payout)
                .maturityDate(fd.getMaturityDate())
                .maturityAmountIfHeld(fd.getMaturityAmount())
                .note(tooEarly
                        ? "Closed within " + props.getFd().getPrematureMinDays() + " days: no interest is paid, only the principal is returned."
                        : "Premature closure: interest at the contracted rate minus " + penalty.toPlainString() + "% for the days held.")
                .build();
    }

    // ------------------------------------------------------------------ admin

    public PageResponse<FdResponse> adminList(FdStatus status, int page, int size) {
        return PageResponse.of(fdRepo.search(status, Util.page(page, size)), f -> toResponse(f, customerName(f.getBankId())));
    }

    public FdResponse adminGet(String fdId) {
        FixedDepositEntity fd = fdRepo.findById(fdId).orElseThrow(() -> new NotFoundException("Fixed deposit not found."));
        return toResponse(fd, customerName(fd.getBankId()));
    }

    /** Starts the FD from today, optionally at an overridden rate. */
    @Transactional
    public FdResponse approve(String fdId, FdApprovalRequest request) {
        FixedDepositEntity fd = fdRepo.lockById(fdId).orElseThrow(() -> new NotFoundException("Fixed deposit not found."));
        if (fd.getStatus() != FdStatus.PENDING) {
            throw new BankException("FD is already " + fd.getStatus().name().toLowerCase() + ".");
        }
        BigDecimal rate = fd.getInterestRate();
        if (request != null && request.getInterestRate() != null) {
            validateRate(request.getInterestRate());
            rate = request.getInterestRate();
        }
        FdQuoteResponse quote = quote(fd.getPrincipal(), rate, fd.getTenureMonths(), LocalDate.now());
        fd.setInterestRate(rate);
        fd.setDepositDate(quote.getDepositDate());
        fd.setMaturityDate(quote.getMaturityDate());
        fd.setMaturityAmount(quote.getMaturityAmount());
        fd.setExpectedInterest(quote.getTotalInterest());
        fd.setStatus(FdStatus.ACTIVE);
        fd.setAdminComment(request == null ? null : Util.blankToNull(request.getComment()));
        fd.setApprovedAt(LocalDateTime.now());
        fd.setUpdatedAt(LocalDateTime.now());
        fdRepo.save(fd);
        log.info("FD {} approved at {}%", fdId, rate);
        return toResponse(fd, customerName(fd.getBankId()));
    }

    /** Rejects a pending FD and refunds the principal; the original booking is marked REVERSED. */
    public FdResponse reject(String fdId, DecisionRequest request) {
        String comment = Util.blankToNull(request == null ? null : request.getComment());
        if (comment == null) {
            throw new BankException("Please give a reason for rejecting the FD.");
        }
        FixedDepositEntity peek = fdRepo.findById(fdId).orElseThrow(() -> new NotFoundException("Fixed deposit not found."));
        return executor.run(() -> {
            BankAccountEntity account = accountService.lockAccount(peek.getAccountNo());
            FixedDepositEntity fd = fdRepo.lockById(fdId).orElseThrow();
            if (fd.getStatus() != FdStatus.PENDING) {
                throw new BankException("FD is already " + fd.getStatus().name().toLowerCase() + ".");
            }
            TransactionStatusEntity refund = ledgerService.credit(account, LedgerService.Posting.builder()
                    .type(TransactionType.FD_REFUND)
                    .amount(fd.getPrincipal())
                    .reference(fd.getFdId())
                    .description("Refund for rejected FD " + fd.getFdId())
                    .toBankId(fd.getBankId())
                    .channel("FD_SIM")
                    .build());
            txnRepo.findById(fd.getFundingTransactionId()).ifPresent(booking -> {
                booking.setStatus(TransactionStatus.REVERSED);
                booking.setUpdatedAt(LocalDateTime.now());
                booking.setUpdateDate(LocalDate.now());
                txnRepo.save(booking);
            });
            fd.setStatus(FdStatus.REJECTED);
            fd.setAdminComment(comment);
            fd.setPayoutAmount(fd.getPrincipal());
            fd.setPayoutTransactionId(refund.getTransactionId());
            fd.setClosedAt(LocalDateTime.now());
            fd.setUpdatedAt(LocalDateTime.now());
            fdRepo.save(fd);
            log.info("FD {} rejected and principal refunded", fdId);
            return toResponse(fd, customerName(fd.getBankId()));
        });
    }

    // ------------------------------------------------------------------ helpers

    private FixedDepositEntity own(String bankId, String fdId) {
        return fdRepo.findByFdIdAndBankId(fdId, bankId).orElseThrow(() -> new NotFoundException("Fixed deposit not found."));
    }

    private String customerName(String bankId) {
        return userRepo.findById(bankId).map(Util::fullName).orElse(null);
    }

    private static long count(List<FixedDepositEntity> fds, FdStatus status) {
        return fds.stream().filter(f -> f.getStatus() == status).count();
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private FdResponse toResponse(FixedDepositEntity f, String customerName) {
        Long daysToMaturity = f.getStatus() == FdStatus.ACTIVE
                ? Math.max(ChronoUnit.DAYS.between(LocalDate.now(), f.getMaturityDate()), 0) : null;
        return FdResponse.builder()
                .fdId(f.getFdId())
                .bankId(f.getBankId())
                .customerName(customerName)
                .accountNo(f.getAccountNo())
                .principal(f.getPrincipal())
                .interestRate(f.getInterestRate())
                .tenureMonths(f.getTenureMonths())
                .compoundingPerYear(f.getCompoundingPerYear())
                .depositDate(f.getDepositDate())
                .maturityDate(f.getMaturityDate())
                .expectedInterest(f.getExpectedInterest())
                .maturityAmount(f.getMaturityAmount())
                .status(f.getStatus().name())
                .closureType(f.getClosureType() == null ? null : f.getClosureType().name())
                .payoutAmount(f.getPayoutAmount())
                .penaltyRate(f.getPenaltyRate())
                .fundingTransactionId(f.getFundingTransactionId())
                .payoutTransactionId(f.getPayoutTransactionId())
                .adminComment(f.getAdminComment())
                .approvedAt(f.getApprovedAt())
                .closedAt(f.getClosedAt())
                .createdAt(f.getCreatedAt())
                .daysToMaturity(daysToMaturity)
                .simulated(true)
                .build();
    }
}
