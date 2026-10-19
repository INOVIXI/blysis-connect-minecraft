package com.uxplima.blysis.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.uxplima.blysis.paper.adapter.inbound.command.CreditsCommand;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rankings a player may ask for are offered as they type, and a word that names none is not one of them.
 *
 * <p>Found live: "/credits top" offered nothing after it, so nobody could find "topups month", and
 * "/credits top balance forever" showed the all-time list as though "forever" were a period.
 */
final class TheRankingsAreOfferedTest {

    @Test
    @DisplayName("credits top offers each list, and each period after it")
    void theListsAndPeriodsAreOffered(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        try (InputStream shipped = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("commands.conf"), "commands.conf")) {
            Files.copy(shipped, file);
        }
        ParamResolvers resolvers = ParamResolvers.withDefaults()
                .replacer(FromConfig.class, ConfiguredCommands.load(file).replacer());
        Replies replies = new Replies(mock(Messages.class), mock(Scheduler.class), Optional::empty);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(AnnotatedCommands.buildNode(new CreditsCommand(replies, mock(Messages.class)), resolvers));
        CommandSender player = mock(CommandSender.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);

        assertThat(offered(dispatcher, "credits top ", source)).contains("balance", "topups", "latest");
        assertThat(offered(dispatcher, "credits top balance ", source)).contains("day", "month", "year", "all");
    }

    private static List<String> offered(
            CommandDispatcher<CommandSourceStack> dispatcher, String typed, CommandSourceStack source) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(typed, source)).join().getList().stream()
                .map(Suggestion::getText)
                .toList();
    }
}
