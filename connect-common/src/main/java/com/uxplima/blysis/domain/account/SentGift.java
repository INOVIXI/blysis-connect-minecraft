package com.uxplima.blysis.domain.account;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/** A gift this player sent: still waiting, and so takeable back, or accepted. */
public record SentGift(String id, String title, @Nullable String icon, int quantity, String to, boolean waiting) {

    public SentGift {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(to, "to");
    }
}
