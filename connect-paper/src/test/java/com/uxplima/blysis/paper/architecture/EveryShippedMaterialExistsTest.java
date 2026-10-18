package com.uxplima.blysis.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.Material;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every material a shipped file names is one this server version has. A name it does not know draws an
 * empty slot in a window, or turns a voucher into paper, and nothing says why.
 */
final class EveryShippedMaterialExistsTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /** {@code material = "PAPER"}, quoted or bare, at the start of a line or after a brace or a comma. */
    private static final Pattern NAMED = Pattern.compile("(?m)(?:^|[{,])\\s*material\\s*=\\s*\"?([^\"\\n,}]+)\"?");

    @Test
    @DisplayName("every material a shipped file names is one the server knows")
    void everyShippedMaterialExists() throws IOException {
        List<String> unknown = new ArrayList<>();
        for (String written : named()) {
            if (written.indexOf('%') >= 0) {
                continue;
            }
            if (Material.matchMaterial(written) == null) {
                unknown.add(written);
            }
        }
        assertThat(unknown)
                .describedAs("names this server version does not have")
                .isEmpty();
    }

    @Test
    @DisplayName("the shipped files really name a material, so this cannot pass by reading nothing")
    void theFilesNameMaterials() throws IOException {
        assertThat(named()).describedAs("the voucher's material at least").isNotEmpty();
    }

    /** Every material the files a first boot writes name, the words left out: a word is not a material. */
    private static Set<String> named() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".conf"))
                    .filter(path -> !path.toString().contains("messages"))
                    .toList()) {
                Matcher named = NAMED.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (named.find()) {
                    String written = named.group(1).trim();
                    if (!written.isEmpty()) {
                        found.add(written);
                    }
                }
            }
        }
        return found;
    }
}
