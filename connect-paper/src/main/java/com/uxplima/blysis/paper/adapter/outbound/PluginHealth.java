package com.uxplima.blysis.paper.adapter.outbound;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.uxplima.blysis.domain.LinkState;
import com.uxplima.uxmlib.health.HealthCheck;
import com.uxplima.uxmlib.health.HealthResult;

/**
 * The things that can be wrong with this plugin, each answering in one line.
 *
 * <p>Everything this plugin could not read at boot went to the console and nowhere else. An operator who
 * was not watching it, or who is not the person with access to it, had no way to ask again, and the only
 * way to find out was to notice.
 *
 * <p>Each check takes what it reads as a supplier rather than the object it reads from, so the checks are
 * this plugin's own rules and the wiring stays in the composition root. Every one is read only, and a
 * storage check opens a connection, which is why the whole run is on the async lane.
 */
public final class PluginHealth {

    private PluginHealth() {}

    /**
     * Whether this server reaches its site.
     *
     * <p>Not set up is a failure: a server with no address or token delivers nothing, and an operator asking
     * the doctor is the one who needs to hear it.
     */
    public static HealthCheck site(Supplier<Optional<LinkState>> state) {
        return check(
                "site",
                () -> state.get()
                        .map(now -> switch (now) {
                            case CONNECTED -> HealthResult.ok("connected");
                            case CONNECTING -> HealthResult.warn("connecting");
                            case UNREACHABLE -> HealthResult.warn("the site does not answer; the console says why");
                            case STOPPED -> HealthResult.fail("the site refused this server; the console says why");
                        })
                        .orElseGet(() -> HealthResult.fail("site.address and site.token in config.conf are empty")));
    }

    /** How many outcomes the site has not acknowledged yet. A few is normal; they go with the next report. */
    public static HealthCheck journal(Supplier<Optional<Integer>> unreported) {
        return check(
                "journal",
                () -> unreported
                        .get()
                        .map(count -> count == 0
                                ? HealthResult.ok("every outcome reached the site")
                                : HealthResult.warn(count + " outcomes wait for the site"))
                        .orElseGet(() -> HealthResult.ok("no journal: this server is not connected")));
    }

    /** How many windows were read, because a window that did not parse is a menu that cannot open. */
    public static HealthCheck windows(IntSupplier read) {
        return check("windows", () -> {
            int count = read.getAsInt();
            return count == 0
                    ? HealthResult.fail("no window was read: the store cannot open")
                    : HealthResult.ok(count + " read");
        });
    }

    /**
     * Whether PlaceholderAPI took our placeholders. A warning and not a failure: everything else works without
     * it, and only our values in somebody else's scoreboard read as nothing.
     */
    public static HealthCheck placeholders(BooleanSupplier published) {
        return check(
                "placeholders",
                () -> published.getAsBoolean()
                        ? HealthResult.ok("published")
                        : HealthResult.warn("PlaceholderAPI is not on this server, so %blysis_...% publishes nothing"));
    }

    private static HealthCheck check(String name, Supplier<HealthResult> answer) {
        return new HealthCheck() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public HealthResult check() {
                return answer.get();
            }
        };
    }
}
