package com.uxplima.blysis.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An amount a player or staff types, read the way the site will read it.
 *
 * <p>Found live: the console added 500000000 credits, which this side took and the site refused as an
 * invalid request, so staff read a protocol code instead of a reason. The ceiling here is the site's, so a
 * number the site cannot hold is refused here in words.
 */
final class AmountsTest {

    @Test
    @DisplayName("the most an amount may be is the most the site holds, 99999999")
    void theCeilingIsTheSites() {
        assertThat(Amounts.parse("99999999")).contains(new BigDecimal("99999999"));
        assertThat(Amounts.parse("99999999.01")).isEmpty();
        assertThat(Amounts.parse("500000000")).isEmpty();
        assertThat(Amounts.parse("1e9")).isEmpty();
    }

    @Test
    @DisplayName("an amount is more than zero, with two decimals at most, written with a dot or a comma")
    void anAmount() {
        assertThat(Amounts.parse("1,5")).contains(new BigDecimal("1.5"));
        assertThat(Amounts.parse(" 2.50 ")).contains(new BigDecimal("2.5"));
        assertThat(Amounts.parse("0.001")).isEmpty();
        assertThat(Amounts.parse("1,000")).isEmpty();
        assertThat(Amounts.parse("0")).isEmpty();
        assertThat(Amounts.parse("-1")).isEmpty();
        assertThat(Amounts.parse("NaN")).isEmpty();
        assertThat(Amounts.parse("0x10")).isEmpty();
    }

    @Test
    @DisplayName("an amount reads without an exponent")
    void shownPlainly() {
        assertThat(Amounts.parse("1e2").map(Amounts::shown)).contains("100");
        assertThat(Amounts.shown(new BigDecimal("1.50"))).isEqualTo("1.5");
    }
}
