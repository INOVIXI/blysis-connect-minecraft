package com.uxplima.blysis.domain.account;

import java.math.BigDecimal;
import java.util.Objects;

/** The community goal, as the site's banner shows it. Inactive when the operator set none. */
public record Goal(boolean active, String title, BigDecimal raised, BigDecimal goal, int percent) {

    public static final Goal NONE = new Goal(false, "", BigDecimal.ZERO, BigDecimal.ZERO, 0);

    public Goal {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(raised, "raised");
        Objects.requireNonNull(goal, "goal");
    }
}
