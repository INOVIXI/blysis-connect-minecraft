package com.uxplima.blysis.domain.account;

import java.math.BigDecimal;
import java.util.Optional;

/** A number of credits as a player types it: more than zero, two decimals at most, a dot or a comma. */
public final class Amounts {

    private static final BigDecimal MOST = new BigDecimal("1000000000");

    private Amounts() {}

    public static Optional<BigDecimal> parse(String typed) {
        try {
            BigDecimal amount = new BigDecimal(typed.strip().replace(',', '.'));
            if (amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MOST) > 0) {
                return Optional.empty();
            }
            BigDecimal plain = amount.stripTrailingZeros();
            return Optional.of(plain.scale() < 0 ? plain.setScale(0) : plain);
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Like {@link #parse}, but zero is an amount too: setting a balance to nothing is allowed. */
    public static Optional<BigDecimal> parseOrZero(String typed) {
        if (typed.strip().equals("0")) {
            return Optional.of(BigDecimal.ZERO);
        }
        return parse(typed);
    }

    /** How an amount reads: no trailing zeros, no exponent. */
    public static String shown(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
