package com.uxplima.blysis.paper.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.uxplima.blysis.paper.adapter.inbound.command.AdminCommand;
import com.uxplima.blysis.paper.adapter.outbound.PluginSettings;
import com.uxplima.blysis.paper.testing.DirectScheduler;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageResolver;
import com.uxplima.uxmlib.text.language.PlayerLanguages;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The commands read a player in the language the player chose, not the one the client is set to.
 *
 * <p>Found live: Mallory chose Turkish, and "/blysis" refused her in English. The plugin never handed the
 * command layer the choice, so the library's lines and a command word for one language followed the client.
 */
final class CommandsReadTheChosenLanguageTest {

    @Test
    @DisplayName("a word for Turkish readers is there for a player who chose Turkish on an English client")
    void theChoiceDecides(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, """
                commands {
                  blysis {
                    name = "blysis"
                    subcommands {
                      lang { name = "lang", localized-aliases { tr = ["dil"] } }
                    }
                  }
                }
                """, StandardCharsets.UTF_8);
        Path config = folder.resolve("config.conf");
        Files.writeString(config, "language { default = \"en\" }\n", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(config);
        PlayerLanguages chosen = PlayerLanguages.inMemory();
        LanguageResolver resolver = new LanguageResolver(settings.current().language(), chosen);
        LanguageChoices choices = new LanguageChoices(
                () -> Set.of(Locale.ENGLISH, Locale.forLanguageTag("tr")),
                resolver,
                settings.current().language());
        Messages messages = new Messages(MessageCatalogLoader.fromNodes(Map.of(), Locale.ENGLISH), resolver);

        Player mallory = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(mallory.getUniqueId()).thenReturn(id);
        when(mallory.locale()).thenReturn(Locale.ENGLISH);
        when(mallory.hasPermission(anyString())).thenReturn(true);
        choices.choose(id, Locale.forLanguageTag("tr"));
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(mallory);

        CommandNode<CommandSourceStack> root = AnnotatedCommands.buildNode(
                new AdminCommand(settings, messages, "test", new DirectScheduler(), List::of, choices),
                PluginModule.commandResolvers(messages, resolver, ConfiguredCommands.load(file), choices));

        CommandNode<CommandSourceStack> dil = root.getChild("dil");
        assertThat(dil).isNotNull();
        assertThat(dil != null && dil.canUse(source)).isTrue();
    }
}
