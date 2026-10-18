package com.uxplima.blysis.domain.account;

import java.math.BigDecimal;
import java.util.Objects;

/** A player's account on the site, or the fact that they have none yet. */
public record Profile(boolean linked, String username, BigDecimal balance, boolean receiveGifts) {

    /** A player who has not linked an account. */
    public static final Profile UNLINKED = new Profile(false, "", BigDecimal.ZERO, false);

    public Profile {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(balance, "balance");
    }
}
