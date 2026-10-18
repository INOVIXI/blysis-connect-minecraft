package com.uxplima.blysis.velocity.adapter.outbound;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.uxplima.blysis.application.port.Console;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;

/** The proxy's console, where a job meant for the network root runs. */
public final class ProxyConsole implements Console {

    private final CommandManager commands;
    private final CommandSource console;

    public ProxyConsole(CommandManager commands, CommandSource console) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.console = Objects.requireNonNull(console, "console");
    }

    @Override
    public CompletableFuture<ConsoleAnswer> run(String command) {
        Objects.requireNonNull(command, "command");
        String line = command.startsWith("/") ? command.substring(1) : command;
        return commands.executeAsync(console, line)
                .thenApply(ran -> new ConsoleAnswer(ran, ran ? "" : "the proxy knows no such command"));
    }

    /** A command word and every alias the proxy registered beside it. */
    @Override
    public Set<String> namesOf(String word) {
        Objects.requireNonNull(word, "word");
        Set<String> names = new HashSet<>();
        names.add(word);
        CommandMeta meta = commands.getCommandMeta(word);
        if (meta != null) {
            meta.getAliases().forEach(alias -> names.add(alias.toLowerCase(Locale.ROOT)));
        }
        return names;
    }
}
