package com.uxplima.blysis.domain.account;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a purchase would take from one player for one basket, as the site prices it for them: each one, and
 * all of them. It differs from the catalogue's price for a member who owns a cheaper product on the same
 * shelf, or who buys enough for a bulk discount.
 */
public record Quote(BigDecimal each, BigDecimal total) {

    public Quote {
        Objects.requireNonNull(each, "each");
        Objects.requireNonNull(total, "total");
    }
}
