package com.uxplima.blysis.application.port;

import com.uxplima.blysis.domain.PlayerRef;

/** Who is on this server right now. */
@FunctionalInterface
public interface Presence {

    /** True when the player is online here: by id when the site knows it, by name when it does not. */
    boolean isOnline(PlayerRef player);
}
