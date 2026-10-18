package com.uxplima.blysis.domain.account;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/** One thing bought and not yet claimed, or a gift that waits to be accepted. */
public record ChestItem(
        String id,
        String title,
        @Nullable String icon,
        int quantity,
        boolean pendingGift,
        @Nullable String from) {

    public ChestItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
    }
}
