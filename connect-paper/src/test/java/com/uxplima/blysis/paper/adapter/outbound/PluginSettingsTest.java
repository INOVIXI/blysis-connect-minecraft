package com.uxplima.blysis.paper.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.uxplima.uxmlib.text.language.LanguageSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that the three language keys reach the resolver.
 *
 * <p>The keys are the same in every plugin of ours, and an operator who writes one and gets no change has no
 * way to tell a typo from a plugin that never read the file. This is the test that says the file is read.
 */
final class PluginSettingsTest {

    @Test
    @DisplayName("the language block is read into the settings the resolver asks for")
    void readsTheLanguageBlock(@TempDir Path folder) throws IOException {
        LanguageSettings language = settingsFrom(folder, """
                language {
                  default       = "tr"
                  follow-client = false
                  force         = "de"
                }
                """);

        assertThat(language.defaultLocale()).isEqualTo(Locale.forLanguageTag("tr"));
        assertThat(language.followClient()).isFalse();
        assertThat(language.forcedLocale()).contains(Locale.forLanguageTag("de"));
    }

    @Test
    @DisplayName("an empty force means no language is forced")
    void anEmptyForceForcesNothing(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "language { force = \"\" }\n").forcedLocale())
                .isEmpty();
    }

    @Test
    @DisplayName("a file with no language block follows the client in English")
    void defaultsWithoutTheBlock(@TempDir Path folder) throws IOException {
        LanguageSettings language = settingsFrom(folder, "connection { retry-first-seconds = 3 }\n");

        assertThat(language.defaultLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(language.followClient()).isTrue();
        assertThat(language.forcedLocale()).isEmpty();
    }

    private static LanguageSettings settingsFrom(Path folder, String body) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return new PluginSettings(file).current().language();
    }

    @Test
    @DisplayName("a server with no address or token starts unconfigured, with the shipped blocklist")
    void startsUnconfigured(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "language { default = \"en\" }\n", StandardCharsets.UTF_8);

        var connect = new PluginSettings(file).current().connect();

        assertThat(connect.configured()).isFalse();
        assertThat(connect.blocked()).contains("op", "stop");
        assertThat(connect.toString()).doesNotContain("gc_");
    }

    @Test
    @DisplayName("a token that is not one the site issued is refused, and the reload keeps the last good file")
    void refusesAMalformedToken(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "site { address = \"https://example.com\", token = \"\" }\n", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(file);
        Files.writeString(
                file, "site { address = \"https://example.com\", token = \"secret\" }\n", StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThatThrownBy(settings::reload)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret");
        assertThat(settings.current().connect().address()).isEqualTo("https://example.com");
    }
}
