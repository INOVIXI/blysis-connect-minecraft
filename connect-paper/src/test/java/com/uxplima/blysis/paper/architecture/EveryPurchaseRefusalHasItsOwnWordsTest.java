package com.uxplima.blysis.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.blysis.paper.application.message.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every refusal the site's protocol names for a purchase has a line of its own.
 *
 * <p>A code without one reads as "the site refused that: limit_reached", which tells a player nothing they
 * can act on. The list is the protocol's, so a code the site adds is a line added here first.
 */
final class EveryPurchaseRefusalHasItsOwnWordsTest {

    private static final List<String> PURCHASE_REFUSALS = List.of(
            "not_linked",
            "not_found",
            "not_in_game",
            "insufficient_balance",
            "out_of_stock",
            "quantity_invalid",
            "limit_reached",
            "field_invalid",
            "recipient_unknown",
            "not_giftable",
            "recipient_refuses_gifts",
            "unavailable");

    @Test
    @DisplayName("every refusal of a purchase has its own words")
    void everyRefusalHasWords() {
        Set<String> paths = Arrays.stream(Keys.values()).map(Keys::path).collect(Collectors.toSet());
        assertThat(PURCHASE_REFUSALS).allSatisfy(code -> assertThat(paths).contains("refused." + code));
    }
}
