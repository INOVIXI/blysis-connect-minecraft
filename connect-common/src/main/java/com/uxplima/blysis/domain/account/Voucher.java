package com.uxplima.blysis.domain.account;

import java.math.BigDecimal;
import java.util.Objects;

/** A voucher: its id is the whole of it, and an item that carries the id is only its look. */
public record Voucher(String id, BigDecimal amount) {

    public Voucher {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(amount, "amount");
        if (!id.startsWith("gv_")) {
            throw new IllegalArgumentException("a voucher id starts with gv_");
        }
    }
}
