package com.uxplima.blysis.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.blysis.application.port.Accounts;
import com.uxplima.blysis.domain.LinkState;

/**
 * What a command or a menu needs from the connection: the player's calls, the kept profiles and shelves, the
 * once-only runner, and where the connection stands. It lives as long as one connection, so a reload that
 * changed the token hands every surface a new one.
 */
public record SiteSession(
        Accounts accounts,
        Profiles profiles,
        CatalogCache catalog,
        Boards boards,
        Once once,
        Supplier<LinkState> state) {

    public SiteSession {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(profiles, "profiles");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(boards, "boards");
        Objects.requireNonNull(once, "once");
        Objects.requireNonNull(state, "state");
    }

    /** False while the site does not answer: nothing that moves credits is attempted then. */
    public boolean reachable() {
        return state.get() == LinkState.CONNECTED;
    }
}
