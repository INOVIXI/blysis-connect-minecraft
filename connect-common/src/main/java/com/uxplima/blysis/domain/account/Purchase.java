package com.uxplima.blysis.domain.account;

import java.math.BigDecimal;
import java.util.Objects;

/** A purchase the site accepted: what it cost, what is left, and whether it went to someone else. */
public record Purchase(String orderNumber, BigDecimal total, BigDecimal balance, boolean gift) {

    public Purchase {
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(balance, "balance");
    }
}
