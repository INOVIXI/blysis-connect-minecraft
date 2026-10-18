package com.uxplima.blysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.blysis.application.port.Console;
import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.application.port.Site;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.Backoff;
import com.uxplima.blysis.domain.CommandBlocklist;
import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.Job;
import com.uxplima.blysis.domain.JobResult;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.Poll;
import com.uxplima.blysis.domain.SiteEvent;
import com.uxplima.blysis.domain.SiteSettings;
import com.uxplima.blysis.domain.StatusPush;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class StayConnectedTest {

    private static final Hello HELLO = new Hello(
            "s1",
            "Survival",
            "Example",
            "https://example.com",
            new SiteSettings(Duration.ofSeconds(25), Duration.ofSeconds(30), Duration.ofSeconds(120), "c-1"),
            null);

    /** A site that plays back what each call should do, in order. */
    private static final class ScriptedSite implements Site {
        final Deque<Supplier<Poll>> polls = new ArrayDeque<>();
        final List<@Nullable String> cursors = new ArrayList<>();
        final List<StatusPush> statuses = new ArrayList<>();
        int hellos;

        @Override
        public Hello hello(String software, String softwareVersion, String role) {
            hellos++;
            return HELLO;
        }

        @Override
        public Poll poll(@Nullable String eventCursor) {
            cursors.add(eventCursor);
            Supplier<Poll> next = polls.poll();
            return next == null ? new Poll(List.of(), List.of(), "e-0", "c-1") : next.get();
        }

        @Override
        public void status(StatusPush push) {
            statuses.add(push);
        }

        @Override
        public Set<String> report(List<JobResult> results) {
            return Set.of();
        }
    }

    private final ScriptedSite site = new ScriptedSite();
    private final List<String> said = new ArrayList<>();
    private final List<SiteEvent> events = new ArrayList<>();
    private final List<String> catalogues = new ArrayList<>();
    private final List<String> ran = new ArrayList<>();

    private final LinkWatcher watcher = new LinkWatcher() {
        @Override
        public void stateChanged(LinkState state, String detail) {
            said.add(state.name());
        }

        @Override
        public void events(List<SiteEvent> arrived) {
            events.addAll(arrived);
        }

        @Override
        public void catalogChanged(String version) {
            catalogues.add(version);
        }
    };

    private final StayConnected connection = new StayConnected(
            site,
            new DeliverJobs(
                    new MemoryJournal(),
                    command -> {
                        ran.add(command);
                        return CompletableFuture.completedFuture(new Console.ConsoleAnswer(true, ""));
                    },
                    player -> true,
                    site,
                    () -> new CommandBlocklist(List.of()),
                    Duration.ofSeconds(1)),
            watcher,
            new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(60), () -> 0.5),
            "Paper",
            "26.2",
            "server");

    @Test
    @DisplayName("hello comes first and once, then poll after poll with the cursor the site gave")
    void greetsThenPolls() {
        site.polls.add(() -> new Poll(List.of(), List.of(), "e-7", "c-1"));

        assertThat(connection.step()).contains(Duration.ZERO);
        assertThat(connection.step()).contains(Duration.ZERO);
        assertThat(connection.step()).contains(Duration.ZERO);

        assertThat(site.hellos).isEqualTo(1);
        assertThat(site.cursors).containsExactly(null, "e-7");
        assertThat(said).containsExactly("CONNECTED");
    }

    @Test
    @DisplayName("a poll's jobs are run and its events and a new catalogue are passed on")
    void deliversWhatAPollBrings() {
        site.polls.add(() -> new Poll(
                List.of(new Job("j1", "g1", 0, "give Steve diamond", null, false, "Diamonds")),
                List.of(new SiteEvent("e-1", "purchase", java.util.Map.of("product", "VIP"))),
                "e-1",
                "c-2"));

        connection.step();
        connection.step();

        assertThat(ran).containsExactly("give Steve diamond");
        assertThat(events).extracting(SiteEvent::type).containsExactly("purchase");
        assertThat(catalogues).containsExactly("c-2");
    }

    @Test
    @DisplayName("a site that does not answer is said once and called again after a growing wait")
    void backsOffQuietly() {
        connection.step();
        for (int i = 0; i < 3; i++) {
            site.polls.add(() -> {
                throw new SiteUnavailable("down");
            });
        }

        Optional<Duration> first = connection.step();
        Optional<Duration> second = connection.step();
        Optional<Duration> third = connection.step();
        Optional<Duration> back = connection.step();

        assertThat(first).contains(Duration.ofSeconds(1));
        assertThat(second).contains(Duration.ofSeconds(2));
        assertThat(third).contains(Duration.ofSeconds(4));
        assertThat(back).contains(Duration.ZERO);
        assertThat(said).containsExactly("CONNECTED", "UNREACHABLE", "CONNECTED");
    }

    @Test
    @DisplayName("a superseded process stops and never calls again")
    void stopsWhenSuperseded() {
        connection.step();
        site.polls.add(() -> {
            throw new SiteRefused(409, "superseded", "Another process took over");
        });

        assertThat(connection.step()).isEmpty();
        assertThat(connection.step()).isEmpty();

        assertThat(connection.state()).isEqualTo(LinkState.STOPPED);
        assertThat(site.cursors).hasSize(1);
    }

    @Test
    @DisplayName("a status goes out only while the site answers")
    void pushesStatusOnlyWhenConnected() {
        StatusPush push = new StatusPush(1, 10, List.of(), "26.2", 20, 5);

        connection.pushStatus(push);
        connection.step();
        connection.pushStatus(push);

        assertThat(site.statuses).hasSize(1);
    }
}
