package com.dcb.service.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * EMI = P x r x (1 + r)^n / ((1 + r)^n - 1), with r the monthly rate and n the number of instalments.
 */
public final class EmiCalculator {

    private static final MathContext MC = new MathContext(24, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private EmiCalculator() {
    }

    public record Instalment(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest,
                             BigDecimal emi, BigDecimal remainingPrincipal) {
    }

    public record Plan(BigDecimal emi, BigDecimal totalInterest, BigDecimal totalRepayment, List<Instalment> schedule) {
    }

    public static BigDecimal monthlyRate(BigDecimal annualRate) {
        return annualRate.divide(TWELVE_HUNDRED, MC);
    }

    public static BigDecimal emi(BigDecimal principal, BigDecimal annualRate, int months) {
        if (annualRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal r = monthlyRate(annualRate);
        BigDecimal growth = BigDecimal.ONE.add(r).pow(months, MC);
        return principal.multiply(r, MC).multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Month-wise schedule. Interest is charged on the outstanding principal each month;
     * the last instalment absorbs rounding so the principal ends at exactly zero.
     */
    public static Plan plan(BigDecimal principal, BigDecimal annualRate, int months, LocalDate firstDueDate) {
        BigDecimal emi = emi(principal, annualRate, months);
        BigDecimal r = monthlyRate(annualRate);
        BigDecimal outstanding = principal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = BigDecimal.ZERO;
        List<Instalment> rows = new ArrayList<>(months);

        for (int i = 1; i <= months; i++) {
            BigDecimal interest = outstanding.multiply(r, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalPart = i == months ? outstanding : emi.subtract(interest).min(outstanding);
            BigDecimal amount = principalPart.add(interest);
            outstanding = outstanding.subtract(principalPart);
            totalInterest = totalInterest.add(interest);
            rows.add(new Instalment(i, firstDueDate.plusMonths(i - 1L), principalPart, interest, amount, outstanding));
        }
        return new Plan(emi, totalInterest, principal.setScale(2, RoundingMode.HALF_UP).add(totalInterest), rows);
    }
}
