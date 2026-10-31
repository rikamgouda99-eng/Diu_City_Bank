package com.dcb;

import com.dcb.service.calc.EmiCalculator;
import com.dcb.service.calc.InterestCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {

    @Test
    void fdQuarterlyCompoundingForOneYear() {
        // 10000 x (1 + 7/400)^4 = 10718.59
        assertEquals(new BigDecimal("10718.59"),
                InterestCalculator.maturityAmount(new BigDecimal("10000"), new BigDecimal("7.00"), 12, 4));
    }

    @Test
    void fdBrokenPeriodEarnsSimpleInterestForTheRemainder() {
        // 13 months quarterly = 4 full quarters + 1/3 quarter
        BigDecimal fourQuarters = InterestCalculator.maturityAmount(new BigDecimal("10000"), new BigDecimal("7.00"), 12, 4);
        BigDecimal thirteenMonths = InterestCalculator.maturityAmount(new BigDecimal("10000"), new BigDecimal("7.00"), 13, 4);
        BigDecimal fifteenMonths = InterestCalculator.maturityAmount(new BigDecimal("10000"), new BigDecimal("7.00"), 15, 4);
        assertTrue(thirteenMonths.compareTo(fourQuarters) > 0);
        assertTrue(thirteenMonths.compareTo(fifteenMonths) < 0);
    }

    @Test
    void fdZeroRateReturnsPrincipal() {
        assertEquals(new BigDecimal("5000.00"),
                InterestCalculator.maturityAmountForDays(new BigDecimal("5000"), BigDecimal.ZERO, 100, 4));
    }

    @Test
    void emiMatchesStandardFormula() {
        // 1,00,000 at 10% for 12 months -> 8791.59
        assertEquals(new BigDecimal("8791.59"), EmiCalculator.emi(new BigDecimal("100000"), new BigDecimal("10"), 12));
    }

    @Test
    void zeroRateEmiIsPrincipalOverMonths() {
        assertEquals(new BigDecimal("1000.00"), EmiCalculator.emi(new BigDecimal("12000"), BigDecimal.ZERO, 12));
    }

    @Test
    void scheduleRepaysExactlyThePrincipal() {
        EmiCalculator.Plan plan = EmiCalculator.plan(new BigDecimal("250000"), new BigDecimal("9.5"), 36, LocalDate.of(2026, 1, 5));
        assertEquals(36, plan.schedule().size());
        BigDecimal principalPaid = plan.schedule().stream().map(EmiCalculator.Instalment::principal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, principalPaid.compareTo(new BigDecimal("250000.00")));
        assertEquals(0, plan.schedule().get(35).remainingPrincipal().signum());
        assertEquals(LocalDate.of(2028, 12, 5), plan.schedule().get(35).dueDate());
        assertEquals(0, plan.totalRepayment().compareTo(new BigDecimal("250000.00").add(plan.totalInterest())));
    }
}
