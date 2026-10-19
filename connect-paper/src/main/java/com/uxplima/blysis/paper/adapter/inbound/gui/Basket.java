package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.blysis.domain.account.Catalog;
import com.uxplima.blysis.domain.account.Quote;
import org.jspecify.annotations.Nullable;

/**
 * What the purchase window is about: one product, how many, for whom, and the answers to its questions so
 * far. Every button that changes one of them opens the window again on a new basket, so the window always
 * draws one finished state.
 *
 * <p>The quote is what the site would take for this basket from this player. It is dropped by anything that
 * changes the price, the quantity and the recipient, and asked again before the window opens.
 */
public record Basket(
        Shelf shelf,
        Catalog.Product product,
        int quantity,
        @Nullable String recipient,
        Map<String, String> answers,
        @Nullable Quote quote) {

    public Basket {
        Objects.requireNonNull(shelf, "shelf");
        Objects.requireNonNull(product, "product");
        answers = Map.copyOf(answers);
        if (quantity < 1) {
            throw new IllegalArgumentException("a quantity is at least one, got: " + quantity);
        }
    }

    /** A basket of one, for oneself, with nothing answered. */
    public static Basket of(Shelf shelf, Catalog.Product product) {
        return new Basket(shelf, product, 1, null, Map.of(), null);
    }

    /** The most the product lets one purchase take, never below one. */
    public int most() {
        int stock = product.stock() == null ? Integer.MAX_VALUE : Math.max(1, product.stock());
        return Math.max(1, Math.min(product.maxQuantity(), stock));
    }

    /** The quantity moved by a step, held between one and the most. */
    public Basket movedBy(int step) {
        return withQuantity(quantity + step);
    }

    /**
     * The quantity a player typed, when it is a whole number from one to the most. Anything else is empty, so
     * the player is told rather than handed a number they did not ask for.
     */
    public Optional<Basket> typed(String typed) {
        int wanted;
        try {
            wanted = Integer.parseInt(typed.strip());
        } catch (NumberFormatException notAWholeNumber) {
            return Optional.empty();
        }
        return wanted < 1 || wanted > most() ? Optional.empty() : Optional.of(withQuantity(wanted));
    }

    public Basket withQuantity(int wanted) {
        return new Basket(shelf, product, Math.max(1, Math.min(most(), wanted)), recipient, answers, null);
    }

    public Basket forRecipient(@Nullable String name) {
        return new Basket(
                shelf, product, quantity, name == null || name.isBlank() ? null : name.strip(), answers, null);
    }

    public Basket answered(String field, String value) {
        Map<String, String> next = new LinkedHashMap<>(answers);
        next.put(field, value);
        return new Basket(shelf, product, quantity, recipient, next, quote);
    }

    /** The first question this product asks that has no answer yet. */
    public Optional<Catalog.Field> nextQuestion() {
        return product.fields().stream()
                .filter(field -> !answers.containsKey(field.name()))
                .findFirst();
    }

    /** The same basket with the site's price for this player. */
    public Basket quoted(Quote price) {
        return new Basket(shelf, product, quantity, recipient, answers, Objects.requireNonNull(price, "price"));
    }

    /** What one costs this player: the site's quote, or the catalogue's price while there is none. */
    public BigDecimal each() {
        return quote == null ? product.price() : quote.each();
    }

    /** What the basket costs this player: the site's quote, or the catalogue's price times the quantity. */
    public BigDecimal total() {
        return quote == null ? product.price().multiply(BigDecimal.valueOf(quantity)) : quote.total();
    }

    /** A purchase for another member. */
    public boolean gift() {
        return recipient != null;
    }
}
