package com.uxplima.blysis.paper.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A purchase the balance does not cover says how much is missing, not a balance below zero.
 *
 * <p>Found live: with 4.99 credits and a total of 5, the buy tile read "Balance after -0.01", a balance no
 * member can have, and nothing on the tile said the purchase would be refused.
 */
final class NotEnoughCreditsTest {

    private static final BigDecimal TOTAL = new BigDecimal("5");

    @Test
    @DisplayName("a covered purchase shows the balance after and nothing missing")
    void covered() {
        assertThat(Purchasing.afterOf(Optional.of(new BigDecimal("12.50")), TOTAL))
                .isEqualTo("7.5");
        assertThat(Purchasing.shortOf(Optional.of(new BigDecimal("12.50")), TOTAL))
                .isEmpty();
        assertThat(Purchasing.afterOf(Optional.of(TOTAL), TOTAL)).isEqualTo("0");
    }

    @Test
    @DisplayName("an uncovered purchase shows what is missing and no balance after")
    void uncovered() {
        assertThat(Purchasing.afterOf(Optional.of(new BigDecimal("4.99")), TOTAL))
                .isEmpty();
        assertThat(Purchasing.shortOf(Optional.of(new BigDecimal("4.99")), TOTAL))
                .isEqualTo("0.01");
    }

    @Test
    @DisplayName("an unknown balance shows neither")
    void unknown() {
        assertThat(Purchasing.afterOf(Optional.empty(), TOTAL)).isEmpty();
        assertThat(Purchasing.shortOf(Optional.empty(), TOTAL)).isEmpty();
    }

    @Test
    @DisplayName("the shipped buy tile lists the missing amount, and every shipped language names it")
    void theTileSaysIt() throws IOException {
        assertThat(resource("menus/basket.conf")).contains("balance after short");
        for (String language : List.of("en", "tr")) {
            assertThat(resource("messages/messages_" + language + ".conf"))
                    .as(language)
                    .containsPattern("short\\s*\\{\\s*label");
        }
    }

    private String resource(String path) throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path), path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
