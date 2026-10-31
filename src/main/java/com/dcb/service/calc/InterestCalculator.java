package com.dcb.service.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Fixed deposit compound interest: A = P x (1 + R / (100 x N)) ^ (N x T).
 * When N x T is not a whole number of periods, the broken period earns simple
 * interest at the period rate, which keeps the whole calculation in BigDecimal.
 */
public final class InterestCalculator {

    private static final MathContext MC = new MathContext(24, RoundingMode.HALF_EVEN);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private InterestCalculator() {
    }

    /** Maturity amount for a tenure in months (T = months / 12). */
    public static BigDecimal maturityAmount(BigDecimal principal, BigDecimal annualRate, int months, int compoundingPerYear) {
        // N x T = N x months / 12 periods
        return compound(principal, annualRate, (long) compoundingPerYear * months, 12, compoundingPerYear);
    }

    /** Maturity amount for a holding period in days (T = days / 365), used for premature closure. */
    public static BigDecimal maturityAmountForDays(BigDecimal principal, BigDecimal annualRate, long days, int compoundingPerYear) {
        return compound(principal, annualRate, compoundingPerYear * days, 365, compoundingPerYear);
    }

    private static BigDecimal compound(BigDecimal principal, BigDecimal annualRate, long periodsNumerator,
                                       long periodsDenominator, int compoundingPerYear) {
        if (annualRate.signum() <= 0 || periodsNumerator <= 0) {
            return principal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal periodRate = annualRate.divide(HUNDRED.multiply(BigDecimal.valueOf(compoundingPerYear)), MC);
        long wholePeriods = periodsNumerator / periodsDenominator;
        long remainder = periodsNumerator % periodsDenominator;

        BigDecimal factor = BigDecimal.ONE.add(periodRate).pow((int) wholePeriods, MC);
        if (remainder > 0) {
            BigDecimal brokenPeriod = BigDecimal.valueOf(remainder).divide(BigDecimal.valueOf(periodsDenominator), MC);
            factor = factor.multiply(BigDecimal.ONE.add(periodRate.multiply(brokenPeriod, MC)), MC);
        }
        return principal.multiply(factor, MC).setScale(2, RoundingMode.HALF_UP);
    }
}
