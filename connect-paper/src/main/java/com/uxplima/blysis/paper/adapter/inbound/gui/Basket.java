package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.blysis.domain.account.Catalog;
import org.jspecify.annotations.Nullable;

/**
 * What the purchase window is about: one product, how many, for whom, and the answers to its questions so
 * far. Every button that changes one of them opens the window again on a new basket, so the window always
 * draws one finished state.
 */
public record Basket(
        Shelf shelf,
        Catalog.Product product,
        int quantity,
        @Nullable String recipient,
        Map<String, String> answers) {

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
        return new Basket(shelf, product, 1, null, Map.of());
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

    public Basket withQuantity(int wanted) {
        return new Basket(shelf, product, Math.max(1, Math.min(most(), wanted)), recipient, answers);
    }

    public Basket forRecipient(@Nullable String name) {
        return new Basket(shelf, product, quantity, name == null || name.isBlank() ? null : name.strip(), answers);
    }

    public Basket answered(String field, String value) {
        Map<String, String> next = new LinkedHashMap<>(answers);
        next.put(field, value);
        return new Basket(shelf, product, quantity, recipient, next);
    }

    /** The first question this product asks that has no answer yet. */
    public Optional<Catalog.Field> nextQuestion() {
        return product.fields().stream()
                .filter(field -> !answers.containsKey(field.name()))
                .findFirst();
    }

    /** What the basket costs, as the catalogue prices it. The site works out the real total. */
    public BigDecimal total() {
        return product.price().multiply(BigDecimal.valueOf(quantity));
    }

    /** A purchase for another member. */
    public boolean gift() {
        return recipient != null;
    }
}
