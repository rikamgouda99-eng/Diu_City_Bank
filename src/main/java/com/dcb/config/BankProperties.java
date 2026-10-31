package com.dcb.config;

import com.dcb.enums.LoanType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every rate, limit and rule used by the simulated banking modules.
 * Values are bound from the "bank.*" keys in application.properties; the defaults
 * below only apply when a key is missing.
 */
@Data
@ConfigurationProperties(prefix = "bank")
public class BankProperties {

    private Txn txn = new Txn();
    private Fd fd = new Fd();
    private Loan loan = new Loan();
    private Card card = new Card();
    private Upload upload = new Upload();

    @Data
    public static class Txn {
        private BigDecimal minAmount = new BigDecimal("1.00");
        private BigDecimal maxAmount = new BigDecimal("500000.00");
        private int miniStatementMax = 10;
    }

    @Data
    public static class Fd {
        private BigDecimal minAmount = new BigDecimal("1000.00");
        private BigDecimal maxAmount = new BigDecimal("10000000.00");
        private int minTenureMonths = 6;
        private int maxTenureMonths = 120;
        /** N in A = P(1 + R/(100N))^(NT). 4 = quarterly. */
        private int compoundingPerYear = 4;
        private BigDecimal minRate = new BigDecimal("3.00");
        private BigDecimal maxRate = new BigDecimal("9.00");
        /** Rate reduction applied on premature closure, in percentage points. */
        private BigDecimal prematurePenaltyRate = new BigDecimal("1.00");
        /** FDs closed before this many days earn no interest. */
        private int prematureMinDays = 7;
        /** Rate slabs: the first slab whose upToMonths >= tenure applies. */
        private List<RateSlab> rates = new ArrayList<>(List.of(
                new RateSlab(12, new BigDecimal("6.50")),
                new RateSlab(36, new BigDecimal("7.00")),
                new RateSlab(120, new BigDecimal("7.25"))));
    }

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RateSlab {
        private int upToMonths;
        private BigDecimal rate;
    }

    @Data
    public static class Loan {
        private BigDecimal minAmount = new BigDecimal("10000.00");
        private BigDecimal maxAmount = new BigDecimal("10000000.00");
        private int minTenureMonths = 6;
        private int maxTenureMonths = 360;
        private BigDecimal minRate = new BigDecimal("1.00");
        private BigDecimal maxRate = new BigDecimal("30.00");
        private Map<LoanType, BigDecimal> rates = new EnumMap<>(Map.of(
                LoanType.PERSONAL, new BigDecimal("11.50"),
                LoanType.HOME, new BigDecimal("8.50"),
                LoanType.EDUCATION, new BigDecimal("9.00"),
                LoanType.VEHICLE, new BigDecimal("9.50")));
    }

    @Data
    public static class Card {
        /** Daily spend limit assigned to a new debit card. */
        private BigDecimal debitDailyLimit = new BigDecimal("50000.00");
        private BigDecimal maxDebitDailyLimit = new BigDecimal("200000.00");
        private BigDecimal defaultCreditLimit = new BigDecimal("50000.00");
        private BigDecimal maxCreditLimit = new BigDecimal("500000.00");
        private int validityYears = 5;
        private BigDecimal minimumDuePercent = new BigDecimal("5");
        /** Day of month on which the simulated credit card statement is generated. */
        private int statementDay = 1;
        private int paymentDueDays = 20;
    }

    @Data
    public static class Upload {
        private long photoMaxBytes = 2L * 1024 * 1024;
        private long kycMaxBytes = 5L * 1024 * 1024;
    }
}
