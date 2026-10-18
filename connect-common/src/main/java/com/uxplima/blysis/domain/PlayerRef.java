package com.uxplima.blysis.domain;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A player as the protocol names one: the game's stable id, when the site knows it, and the name shown now.
 *
 * <p>The id is null for a job the site has only a typed name for, until it sees that player on a server.
 */
public record PlayerRef(@Nullable String id, String name) {

    public PlayerRef {
        Objects.requireNonNull(name, "name");
    }
}
