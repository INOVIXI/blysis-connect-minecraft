package com.uxplima.blysis.domain;

import java.util.List;
import java.util.Objects;

/**
 * Who is on this server and how it runs, sent every status interval. It is the site's only evidence of who
 * is online here, and so of which jobs may come here.
 */
public record StatusPush(int online, int max, List<PlayerRef> players, String version, double tps, double mspt) {

    public StatusPush {
        players = List.copyOf(players);
        Objects.requireNonNull(version, "version");
    }
}
