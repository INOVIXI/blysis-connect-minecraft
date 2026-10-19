package com.uxplima.blysis.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.blysis.paper.adapter.inbound.command.AdminCommand;
import com.uxplima.blysis.paper.adapter.outbound.PluginSettings;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.blysis.paper.testing.DirectScheduler;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageResolver;
import com.uxplima.uxmlib.text.language.PlayerLanguages;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A reload that cannot read the file says so once, and the console holds the reason under this plugin's name.
 *
 * <p>Found live: a broken config.conf answered "The reload failed" and then the server's own "something
 * went wrong while running this command", because the handler threw the error on after saying it, and the
 * console read "Command 'reload' threw an exception".
 */
final class AFailedReloadSaysItOnceTest {

    @Test
    @DisplayName("a broken config.conf is one line to the operator and one logged reason, and the old one stays")
    void oneLineAndTheReason(@TempDir Path folder) throws IOException {
        Path config = folder.resolve("config.conf");
        Files.writeString(config, "language { default = \"en\" }\n", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(config);
        LanguageResolver resolver = new LanguageResolver(settings.current().language(), PlayerLanguages.inMemory());
        LanguageChoices choices = new LanguageChoices(
                () -> Set.of(Locale.ENGLISH), resolver, settings.current().language());
        Messages messages = mock(Messages.class);
        List<Throwable> logged = new ArrayList<>();
        Log log = new Log() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {
                logged.add(cause);
            }

            @Override
            public void debug(String message, Object... args) {}
        };
        AdminCommand admin =
                new AdminCommand(settings, messages, "test", new DirectScheduler(), List::of, choices, log);
        Files.writeString(config, "broken { = = \n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        CommandSender console = mock(CommandSender.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(console);

        assertThatCode(() -> admin.reload(Sender.of(source))).doesNotThrowAnyException();

        verify(messages).send(console, Keys.RELOAD_FAILED);
        verify(messages, never()).send(eq(console), eq(Keys.RELOADED), any(TagResolver[].class));
        assertThat(logged).hasSize(1);
        assertThat(settings.current().language()).isNotNull();
    }
}
