package com.uxplima.blysis.paper.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A balance nobody knows is no row at all. A player with no account on the site read "Balance ?" on the
 * store's credit card; an empty value leaves the row out, and the card says the rest.
 */
final class UnknownBalanceTest {

    @Test
    @DisplayName("a known balance reads as the amount, an unknown one as nothing")
    void anUnknownBalanceIsNothing() {
        assertThat(Purchasing.shownOrNothing(Optional.of(new BigDecimal("12.50"))))
                .isEqualTo("12.5");
        assertThat(Purchasing.shownOrNothing(Optional.empty())).isEmpty();
    }
}
