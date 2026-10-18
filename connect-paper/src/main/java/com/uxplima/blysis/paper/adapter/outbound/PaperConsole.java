package com.uxplima.blysis.paper.adapter.outbound;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Server;
import org.bukkit.command.Command;

import com.uxplima.blysis.application.port.Console;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * The server console, reached on the global region: the main thread on Paper, the global region thread on
 * Folia, which is where a console command is allowed to run on either.
 */
public final class PaperConsole implements Console {

    private final Server server;
    private final Scheduler scheduler;

    public PaperConsole(Server server, Scheduler scheduler) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<ConsoleAnswer> run(String command) {
        Objects.requireNonNull(command, "command");
        CompletableFuture<ConsoleAnswer> answer = new CompletableFuture<>();
        String line = command.startsWith("/") ? command.substring(1) : command;
        scheduler.global(() -> {
            try {
                boolean ran = server.dispatchCommand(server.getConsoleSender(), line);
                answer.complete(new ConsoleAnswer(ran, ran ? "" : "the server knows no such command"));
            } catch (RuntimeException thrown) {
                answer.completeExceptionally(thrown);
            }
        });
        return answer;
    }

    /**
     * The command map is read from the delivering thread. A lookup is a read of a map the server only writes
     * while plugins load, and the worst a race costs is one alias judged under its own name only.
     */
    @Override
    public Set<String> namesOf(String word) {
        Set<String> names = new HashSet<>();
        names.add(word);
        Command command = server.getCommandMap().getCommand(word);
        if (command != null) {
            names.add(command.getName().toLowerCase(Locale.ROOT));
            command.getAliases().forEach(alias -> names.add(alias.toLowerCase(Locale.ROOT)));
        }
        return names;
    }
}
