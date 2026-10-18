package com.uxplima.blysis.velocity.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.velocity.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

/** The operator's command on the proxy: read the files again, say how the connection is, say the version. */
public final class AdminCommand implements SimpleCommand {

    private final Messages messages;
    private final String version;
    private final BooleanSupplier reload;
    private final Supplier<Optional<LinkState>> state;
    private final Supplier<Optional<Integer>> waiting;

    public AdminCommand(
            Messages messages,
            String version,
            BooleanSupplier reload,
            Supplier<Optional<LinkState>> state,
            Supplier<Optional<Integer>> waiting) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.version = Objects.requireNonNull(version, "version");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.state = Objects.requireNonNull(state, "state");
        this.waiting = Objects.requireNonNull(waiting, "waiting");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String verb = invocation.arguments().length == 0 ? "" : invocation.arguments()[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "reload" -> reload(source);
            case "status" ->
                messages.send(
                        source,
                        Keys.PROXY_STATUS,
                        Text.placeholder(
                                "state",
                                state.get()
                                        .map(now -> now.name().toLowerCase(Locale.ROOT))
                                        .orElse("offline")),
                        Text.placeholder(
                                "waiting", waiting.get().map(String::valueOf).orElse("0")));
            case "version" -> messages.send(source, Keys.VERSION, Text.placeholder("version", version));
            default -> messages.send(source, Keys.STAFF_USAGE);
        }
    }

    private void reload(CommandSource source) {
        long started = System.nanoTime();
        if (reload.getAsBoolean()) {
            long ms = (System.nanoTime() - started) / 1_000_000;
            messages.send(source, Keys.RELOADED, Text.placeholder("ms", Long.toString(ms)));
        } else {
            messages.send(source, Keys.RELOAD_FAILED);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return invocation.arguments().length > 1 ? List.of() : List.of("reload", "status", "version");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return Nodes.staff(invocation.source(), "blysis.admin");
    }
}
