package com.uxplima.blysis.paper.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.uxplima.blysis.domain.account.Catalog;
import com.uxplima.blysis.domain.account.Quote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The purchase window shows what the site would take from this player, not the catalogue's price.
 *
 * <p>Found live: the confirm button said "total 5, balance after 38.49" and the purchase took 4, from a
 * member who owned a cheaper product on the same shelf. The window now carries the site's quote, and any
 * change that moves the price drops it, so a stale quote is never shown against a new quantity.
 */
final class QuotedBasketTest {

    private final Catalog.Product big =
            new Catalog.Product("p_big", "Big", "", null, new BigDecimal("5"), true, 10, null, List.of(), List.of());
    private final Basket basket = Basket.of(new Shelf(Catalog.EMPTY, null, false), big);

    @Test
    @DisplayName("without a quote, the catalogue's price stands")
    void theCataloguesPrice() {
        assertThat(basket.withQuantity(3).each()).isEqualByComparingTo("5");
        assertThat(basket.withQuantity(3).total()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("with a quote, each and total are the site's price for this player")
    void theQuote() {
        Basket quoted = basket.withQuantity(3).quoted(new Quote(new BigDecimal("4"), new BigDecimal("12")));

        assertThat(quoted.each()).isEqualByComparingTo("4");
        assertThat(quoted.total()).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("a new quantity or a new recipient drops the quote; an answered question keeps it")
    void whatDropsAQuote() {
        Basket quoted = basket.quoted(new Quote(new BigDecimal("4"), new BigDecimal("4")));

        assertThat(quoted.movedBy(1).quote()).isNull();
        assertThat(quoted.withQuantity(2).quote()).isNull();
        assertThat(quoted.forRecipient("Alex").quote()).isNull();
        assertThat(quoted.answered("colour", "red").quote()).isNotNull();
    }
}
