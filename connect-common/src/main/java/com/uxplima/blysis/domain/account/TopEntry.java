package com.uxplima.blysis.domain.account;

import java.math.BigDecimal;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/** One line of a ranking. The name is null for a member who asked to stay anonymous. */
public record TopEntry(int rank, @Nullable String name, BigDecimal amount) {

    public TopEntry {
        Objects.requireNonNull(amount, "amount");
    }
}
