package com.uxplima.blysis.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.blysis.paper.adapter.inbound.command.AdminCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.ChestCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.CreditsCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.LinkCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.StoreCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.VerifyCommand;
import com.uxplima.blysis.paper.adapter.inbound.command.VoucherCommand;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every argument a command takes has a name in every shipped language.
 *
 * <p>Found live: with Turkish chosen, "/credits top x" answered "x geçerli bir kind değil", the argument
 * named in the English the code declares it in.
 */
final class EveryArgumentIsNamedTest {

    private static final List<Class<?>> COMMANDS = List.of(
            AdminCommand.class,
            ChestCommand.class,
            CreditsCommand.class,
            LinkCommand.class,
            StoreCommand.class,
            VerifyCommand.class,
            VoucherCommand.class);

    @Test
    @DisplayName("each shipped language names every argument under command.argument")
    void everyArgumentIsNamed() throws IOException {
        Set<String> arguments = new TreeSet<>();
        for (Class<?> command : COMMANDS) {
            for (Method method : command.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    Arg arg = parameter.getAnnotation(Arg.class);
                    if (arg != null) {
                        arguments.add(arg.value());
                    }
                }
            }
        }
        assertThat(arguments).isNotEmpty();
        for (String language : List.of("en", "tr")) {
            assertThat(namedIn(language)).as(language).containsAll(arguments);
        }
    }

    private Set<String> namedIn(String language) throws IOException {
        String path = "messages/messages_" + language + ".conf";
        String file;
        try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path), path)) {
            file = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher block = Pattern.compile("\\n  argument \\{\\n(.*?)\\n  }", Pattern.DOTALL)
                .matcher(file);
        Set<String> named = new TreeSet<>();
        if (block.find()) {
            Matcher entry =
                    Pattern.compile("^\\s*([a-z-]+)\\s*=", Pattern.MULTILINE).matcher(block.group(1));
            while (entry.find()) {
                named.add(entry.group(1));
            }
        }
        return named;
    }
}
