package com.uxplima.blysis.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A material the plugin fills in is written {@code %name%}, and the engine reads a name of word characters
 * only. The store once shipped {@code %shelf-item%}: the engine saw no token, the name reached the material
 * lookup as it was written, and every shelf was drawn as stone.
 */
final class EveryMaterialTokenIsAWordTest {

    private static final Path MENUS = Path.of("src", "main", "resources", "menus");
    private static final Pattern MATERIAL = Pattern.compile("material\\s*=\\s*\"?([^\"\\n]*)\"?");
    private static final Pattern TOKEN = Pattern.compile("%([^%]*)%");

    @Test
    @DisplayName("every filled in material is a token of word characters the engine expands")
    void everyTokenIsAWord() throws IOException {
        List<String> offenders = new ArrayList<>();
        int read = 0;
        try (Stream<Path> files = Files.list(MENUS)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".conf")).toList()) {
                Matcher line = MATERIAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (line.find()) {
                    String value = line.group(1).strip();
                    read++;
                    Matcher token = TOKEN.matcher(value);
                    while (token.find()) {
                        if (!token.group(1).matches("\\w+")) {
                            offenders.add(file.getFileName() + ": " + value);
                        }
                    }
                }
            }
        }
        assertThat(read).describedAs("the windows were read").isPositive();
        assertThat(offenders).isEmpty();
    }
}
