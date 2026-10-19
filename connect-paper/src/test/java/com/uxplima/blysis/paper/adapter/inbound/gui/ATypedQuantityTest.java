package com.uxplima.blysis.paper.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.uxplima.blysis.domain.account.Catalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A quantity a player types is taken as typed or refused, never quietly made into another number.
 *
 * <p>Found live on a Bedrock form: "-5" came back as one with nothing said, and a number above what one
 * purchase may take came back as that most. The buttons hold the quantity between one and the most; a typed
 * number outside that is the player asking for something they cannot have, and they are told.
 */
final class ATypedQuantityTest {

    private final Catalog.Product ten =
            new Catalog.Product("p_ten", "Ten", "", null, new BigDecimal("5"), true, 10, null, List.of(), List.of());
    private final Basket basket = Basket.of(new Shelf(Catalog.EMPTY, null, false), ten);

    @Test
    @DisplayName("a number between one and the most is taken as typed")
    void aNumberInRange() {
        assertThat(basket.typed(" 7 "))
                .hasValueSatisfying(typed -> assertThat(typed.quantity()).isEqualTo(7));
        assertThat(basket.typed("10"))
                .hasValueSatisfying(typed -> assertThat(typed.quantity()).isEqualTo(10));
    }

    @Test
    @DisplayName("nothing, zero, a negative, more than the most, or not a whole number is refused")
    void anythingElseIsRefused() {
        for (String typed : List.of("-5", "0", "11", "99999999999", "1e3", "2.5", "", "seven")) {
            assertThat(basket.typed(typed)).as(typed).isEmpty();
        }
    }

    @Test
    @DisplayName("the buttons still hold the quantity between one and the most")
    void theButtonsHold() {
        assertThat(basket.movedBy(-10).quantity()).isEqualTo(1);
        assertThat(basket.movedBy(100).quantity()).isEqualTo(10);
    }
}
