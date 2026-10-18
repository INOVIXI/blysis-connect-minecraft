package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.blysis.domain.account.Catalog;

/** The options window is about one question of the product in one basket. */
public record FieldChoice(Basket basket, Catalog.Field field) {

    public FieldChoice {
        Objects.requireNonNull(basket, "basket");
        Objects.requireNonNull(field, "field");
    }
}
