package com.uxplima.blysis.paper.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The console asks whether a command is still wanted when the main thread reaches it, not when it was queued.
 * A stalled main thread is the case: the delivery gave up on the command while it waited, and it must not
 * run once the server catches up.
 */
final class PaperConsoleTurnTest {

    private final List<Runnable> queued = new ArrayList<>();
    private final Scheduler scheduler = mock(Scheduler.class);
    private final Server server = mock(Server.class);
    private final PaperConsole console;

    PaperConsoleTurnTest() {
        when(scheduler.global(any(Runnable.class))).thenAnswer(call -> {
            queued.add(call.getArgument(0));
            return mock(TaskHandle.class);
        });
        when(server.getConsoleSender()).thenReturn(mock(ConsoleCommandSender.class));
        when(server.dispatchCommand(any(CommandSender.class), anyString())).thenReturn(true);
        console = new PaperConsole(server, scheduler);
    }

    @Test
    @DisplayName("a command given up on while it waited for the main thread does not run when its turn comes")
    void aGivenUpCommandDoesNotRun() {
        AtomicBoolean wanted = new AtomicBoolean(true);
        var late = console.run("say late", wanted::get);
        wanted.set(false);

        queued.forEach(Runnable::run);

        verify(server, never()).dispatchCommand(any(CommandSender.class), anyString());
        assertThat(late).isNotDone();
    }

    @Test
    @DisplayName("a command still wanted when its turn comes runs and answers")
    void aWantedCommandRuns() {
        var answer = console.run("say on time", () -> true);

        queued.forEach(Runnable::run);

        verify(server).dispatchCommand(any(CommandSender.class), any(String.class));
        assertThat(answer).isCompleted();
    }
}
