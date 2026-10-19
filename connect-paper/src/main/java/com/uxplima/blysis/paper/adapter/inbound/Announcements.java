package com.uxplima.blysis.paper.adapter.inbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.SiteEvent;
import com.uxplima.blysis.paper.adapter.outbound.PluginSettings;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.condition.ItemStore;
import com.uxplima.uxmlib.condition.Wallet;
import com.uxplima.uxmlib.condition.action.ActionContext;
import com.uxplima.uxmlib.condition.action.ActionList;
import com.uxplima.uxmlib.content.Operands;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import org.jspecify.annotations.Nullable;

/**
 * What the site's events do in game, run as the operator wrote them, and the one line staff read when the
 * site stops answering.
 *
 * <p>Every event type has three lists in {@code config.conf}: one run for every player online, each in their
 * own language; one run for the player the event names, when they are on this server; and one run once, with
 * the console as the target, for the line that should happen a single time whoever is online. A field of the
 * event is a placeholder of the same name, with a dot written as a hyphen.
 */
public final class Announcements implements LinkWatcher {

    /** Who reads that the site stopped answering, and that it is back. */
    public static final String STAFF_NODE = "blysis.admin.notify";

    private static final String LINKED = "link.confirmed";

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Styler styler;
    private final Supplier<Map<String, PluginSettings.Announcement>> lists;
    private final Replies replies;
    private final AtomicBoolean down = new AtomicBoolean(false);

    public Announcements(
            Server server,
            Scheduler scheduler,
            Messages messages,
            Styler styler,
            Supplier<Map<String, PluginSettings.Announcement>> lists,
            Replies replies) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.styler = Objects.requireNonNull(styler, "styler");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.replies = Objects.requireNonNull(replies, "replies");
    }

    @Override
    public void events(List<SiteEvent> events) {
        Objects.requireNonNull(events, "events");
        for (SiteEvent event : events) {
            if (event.type().equals(LINKED)) {
                forgetProfileOf(event);
            }
            PluginSettings.Announcement said = lists.get().get(event.type().replace('.', '-'));
            if (said != null) {
                scheduler.global(() -> announce(event, said));
            }
        }
    }

    /** Staff read a drop once, and read once more when the site is back. Never once per attempt. */
    @Override
    public void stateChanged(LinkState state, String detail) {
        Objects.requireNonNull(state, "state");
        if ((state == LinkState.UNREACHABLE || state == LinkState.STOPPED) && down.compareAndSet(false, true)) {
            scheduler.global(() -> tellStaff(Keys.STAFF_SITE_DOWN));
        } else if (state == LinkState.CONNECTED && down.compareAndSet(true, false)) {
            scheduler.global(() -> tellStaff(Keys.STAFF_SITE_BACK));
        }
    }

    private void tellStaff(Keys key) {
        for (Player player : server.getOnlinePlayers()) {
            if (player.hasPermission(STAFF_NODE)) {
                messages.send(player, key);
            }
        }
    }

    private void announce(SiteEvent event, PluginSettings.Announcement said) {
        run(server.getConsoleSender(), said.once(), event);
        for (Player player : List.copyOf(server.getOnlinePlayers())) {
            if (!said.everyone().isEmpty()) {
                scheduler.entity(player, () -> run(player, said.everyone(), event));
            }
        }
        Player named = namedPlayer(event);
        if (named != null && !said.player().isEmpty()) {
            scheduler.entity(named, () -> run(named, said.player(), event));
        }
    }

    /**
     * The event's fields as placeholders. A purchase the site keeps anonymous names no player, and the word
     * for somebody stands in, in the reader's language, so a line written with {@code <player>} still reads.
     */
    private TagResolver valuesOf(SiteEvent event, CommandSender reader) {
        List<TagResolver> values = new ArrayList<>();
        for (Map.Entry<String, String> field : event.fields().entrySet()) {
            String name = field.getKey().replace('.', '-');
            values.add(Text.placeholder(name, field.getValue()));
            // A field may share its name with a colour role of the theme, which wins: <title> is one. The same
            // value under event-<name> always reaches the line.
            values.add(Text.placeholder("event-" + name, field.getValue()));
        }
        if (!event.fields().containsKey("player") && !event.fields().containsKey("player.name")) {
            values.add(Text.component("player", messages.render(reader, Keys.ANNOUNCE_SOMEONE)));
        }
        return TagResolver.resolver(values);
    }

    private @Nullable Player namedPlayer(SiteEvent event) {
        String id = event.fields().get("player.id");
        if (id != null) {
            try {
                return server.getPlayer(UUID.fromString(id));
            } catch (IllegalArgumentException notAnId) {
                return null;
            }
        }
        String name = event.fields().getOrDefault("player.name", event.fields().get("player"));
        return name == null ? null : server.getPlayerExact(name);
    }

    private void forgetProfileOf(SiteEvent event) {
        String name = event.fields().getOrDefault("player.name", "");
        PlayerRef ref = new PlayerRef(event.fields().get("player.id"), name);
        replies.session().ifPresent(site -> site.profiles().forget(ref));
    }

    /** One line at a time, so a line the server refuses costs the operator that line and not the rest. */
    private void run(CommandSender who, List<String> lines, SiteEvent event) {
        if (lines.isEmpty()) {
            return;
        }
        ActionContext context = contextFor(who, valuesOf(event, who));
        for (String written : lines) {
            Optional<String> filled = CommandFields.fill(written, event.fields());
            if (filled.isEmpty()) {
                continue;
            }
            String line = filled.get();
            try {
                ActionList.parse(List.of(line)).run(context);
            } catch (RuntimeException failed) {
                server.getLogger().warning("This line of an announcement did nothing: " + line + " (" + failed + ")");
            }
        }
    }

    private ActionContext contextFor(CommandSender who, TagResolver values) {
        ActionContext.Builder built = ActionContext.builder(Operands.standard())
                .target(who)
                .style(line -> styler.tokens(line, messages.localeOf(who)))
                .words(path ->
                        MiniMessage.miniMessage().serialize(messages.render(who, MessageKey.of(path, path), values)))
                .broadcast(server)
                // Credits live on the site and a line here never moves them, so a balance question answers none.
                .wallet(Wallet.empty())
                .consoleSink(line -> server.dispatchCommand(server.getConsoleSender(), line))
                .playerSink(line -> server.dispatchCommand(who, line));
        if (who instanceof Player player) {
            built.player(player).itemStore(ItemStore.inventory()).later(takesTheBarDown(player));
        }
        return built.build();
    }

    /** {@code [bossbar]} puts a bar up and asks the caller to take it down again on the player's thread. */
    private BiConsumer<Duration, Runnable> takesTheBarDown(Player subject) {
        return (delay, what) -> scheduler.entityLater(subject, delay, what);
    }
}
