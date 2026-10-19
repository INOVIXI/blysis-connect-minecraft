package com.uxplima.blysis.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.uxplima.blysis.paper.adapter.inbound.command.AdminCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.LanguageSuggestions;
import com.uxplima.blysis.paper.adapter.outbound.PluginSettings;
import com.uxplima.blysis.paper.testing.DirectScheduler;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageResolver;
import com.uxplima.uxmlib.text.language.PlayerLanguages;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A player who holds only the player's nodes reaches the language commands under {@code /blysis}.
 *
 * <p>Found live: the bare {@code /blysis} is the operator's, and its node sat on the command's word, so the
 * server told every ordinary player that {@code /blysis lang set} was an unknown command.
 */
final class APlayerReachesTheLanguageTest {

    @Test
    @DisplayName("blysis lang set is there for a player with blysis.language, and reload is not")
    void thePlayerReachesTheLanguage(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        try (InputStream shipped = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("commands.conf"), "commands.conf")) {
            Files.copy(shipped, file);
        }
        Path config = folder.resolve("config.conf");
        Files.writeString(config, "language { default = \"en\" }\n", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(config);
        LanguageResolver resolver = new LanguageResolver(settings.current().language(), PlayerLanguages.inMemory());
        LanguageChoices choices = new LanguageChoices(
                () -> Set.of(Locale.ENGLISH), resolver, settings.current().language());
        ParamResolvers resolvers = ParamResolvers.withDefaults()
                .replacer(FromConfig.class, ConfiguredCommands.load(file).replacer())
                .suggestions(LanguageSuggestions.KEY, new LanguageSuggestions(choices));
        Messages messages = new Messages(
                MessageCatalogLoader.fromNodes(Map.of(), Locale.ENGLISH), LocaleSource.ofDefault(Locale.ENGLISH));
        CommandNode<CommandSourceStack> root = AnnotatedCommands.buildNode(
                new AdminCommand(settings, messages, "test", new DirectScheduler(), List::of, choices, Log.NONE),
                resolvers);

        CommandSourceStack player = holding("blysis.language");
        assertThat(root.canUse(player)).isTrue();
        assertThat(child(child(root, "lang"), "set").canUse(player)).isTrue();
        assertThat(child(root, "reload").canUse(player)).isFalse();
    }

    private static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> parent, String name) {
        return Objects.requireNonNull(parent.getChild(name), name);
    }

    private static CommandSourceStack holding(String... nodes) {
        Set<String> held = Set.of(nodes);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenAnswer(call -> held.contains(call.<String>getArgument(0)));
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        return source;
    }
}
