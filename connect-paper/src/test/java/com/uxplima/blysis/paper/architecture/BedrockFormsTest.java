package com.uxplima.blysis.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.uxplima.blysis.paper.testing.SourcePaths;
import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bedrock is the floor, so every window this plugin ships reaches a Floodgate viewer as a native form.
 *
 * <p>The engine draws the form from the very file that paints the chest: one definition, two clients, no
 * second file. Three things can silently take that away. A window can opt out with {@code chest-only}, the
 * composition root can hand the engine the Java only defaults, and the plugin descriptor can fail to say
 * that Floodgate has to load first. In every one of the three, every open falls through to a chest and a
 * Bedrock player is left with a window they cannot use. All three are checked here, because none of them
 * shows up in a compile and none of them shows up on a Java client.
 *
 * <p>The windows are read off the folder rather than listed here, because a list here is the one that
 * goes stale: a window added next month would be a window nobody checked.
 *
 * <p>The editor's own windows are not menu engine windows. They are laid out by slot number in a grammar
 * of their own and drawn by the editor's layout class, so the engine never sees them and neither does a
 * form. They are told apart by that grammar rather than by their names, and a real engine window that
 * lost its items block therefore still fails. Whether the editor should be a form as well is a question
 * for the editor, not for this guard.
 */
final class BedrockFormsTest {

    private static final Path MENUS = Path.of("src", "main", "resources", "menus");

    private static final Path MODULE = SourcePaths.of(BedrockFormsTest.class, "bootstrap", "PluginModule.java");

    private static final Path DESCRIPTOR = Path.of("src", "main", "resources", "paper-plugin.yml");

    /**
     * The windows that are not forms, by file name, with the reason each one is not.
     *
     * <p>Bedrock is the floor, and a window a Bedrock player cannot use is unfinished. A few windows are
     * not menus at all: a container the player fills, an animation the plugin paints itself. Every window
     * this plugin ships is a menu, so the set is empty, and the test below fails when a name in it matches
     * no file, so the set cannot quietly widen.
     */
    private static final java.util.Map<String, String> NOT_A_FORM = java.util.Map.of();

    /** The two plugins the Bedrock detector asks for, in the order it asks. */
    private static final List<String> BEDROCK_PLUGINS = List.of("floodgate", "Geyser-Spigot");

    @Test
    @DisplayName("every shipped window reaches a Bedrock viewer as a form rather than a chest")
    void nowindowIsChestOnly() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path window : windows()) {
            if (NOT_A_FORM.containsKey(window.getFileName().toString())) {
                continue;
            }
            if (specOf(window).chestOnly()) {
                offenders.add(window.getFileName().toString());
            }
        }

        assertThat(offenders)
                .describedAs("A chest only window is a window a Bedrock player cannot use. Take the key out.")
                .isEmpty();
    }

    @Test
    @DisplayName("every shipped window gives a Bedrock viewer something to tap")
    void everywindowHasAButton() throws IOException {
        List<String> empty = new ArrayList<>();

        for (Path window : windows()) {
            if (NOT_A_FORM.containsKey(window.getFileName().toString())) {
                continue;
            }
            MenuSpec spec = specOf(window);
            boolean tappable = spec.items().values().stream().anyMatch(BedrockFormsTest::becomesAButton);
            if (!tappable) {
                empty.add(window.getFileName().toString());
            }
        }

        assertThat(empty)
                .describedAs("A form with no button is a form with nothing in it. Give the window a row or a click.")
                .isEmpty();
    }

    /**
     * A tile with more than one gesture asks a Bedrock player which one they meant, and a tap has no left and no
     * right. Found live: the chest tile asked a phone player to choose between "left click" and "right click".
     */
    @Test
    @DisplayName("a tile with more than one gesture names each, so a Bedrock form asks in words a tap can mean")
    void everyGestureOfAManyGestureTileIsNamed() throws IOException {
        List<String> unnamed = new ArrayList<>();

        for (Path window : windows()) {
            specOf(window).items().forEach((name, item) -> {
                List<ClickKind> bound = java.util.Arrays.stream(ClickKind.values())
                        .filter(kind -> kind != ClickKind.ANY)
                        .filter(kind -> !item.click()
                                .actions()
                                .getOrDefault(kind, List.of())
                                .isEmpty())
                        .toList();
                if (bound.size() > 1) {
                    bound.stream()
                            .filter(kind -> item.click().nameFor(kind).isEmpty())
                            .forEach(kind -> unnamed.add(window.getFileName() + " " + name + " " + kind));
                }
            });
        }

        assertThat(unnamed)
                .describedAs("Name them in the click block: names { left = \"@...\", right = \"@...\" }.")
                .isEmpty();
    }

    /**
     * A tile tells a player something: a balance, a setting, where a thing stands. The form keeps a button
     * only for what answers a tap, so a tile that answers none is a fact a Bedrock player never reads. The
     * store's credit card was one: a Java player saw the balance on it and a Bedrock player saw nothing.
     */
    @Test
    @DisplayName("every tile that tells a player something answers a tap, so a Bedrock viewer reads it too")
    void everyTileIsAButton() throws IOException {
        List<String> silent = new ArrayList<>();

        for (Path window : windows()) {
            specOf(window).items().forEach((name, item) -> {
                boolean tile = item.lore().stream().anyMatch(line -> line.contains("tile:"));
                if (tile && !becomesAButton(item)) {
                    silent.add(window.getFileName() + " " + name);
                }
            });
        }

        assertThat(silent)
                .describedAs("Give each a click: a Bedrock form draws no tile that answers no tap.")
                .isEmpty();
    }

    @Test
    @DisplayName("every window named as not a form is still shipped, so the set cannot rot")
    void theexemptedWindowsAreStillShipped() throws IOException {
        List<String> shipped = windows().stream()
                .map(window -> window.getFileName().toString())
                .toList();

        assertThat(shipped)
                .describedAs(
                        "a name here that matches no file exempts nothing and hides that the fence" + " has widened")
                .containsAll(NOT_A_FORM.keySet());
    }

    @Test
    @DisplayName("the composition root hands the engine a resolved Bedrock detector and screen")
    void theengineIsWiredForBedrock() throws IOException {
        String source = Files.readString(MODULE, StandardCharsets.UTF_8);

        assertThat(source)
                .describedAs("Without the detector the engine believes every viewer is a Java player.")
                .contains("BedrockDetector.forServer(");
        assertThat(source)
                .describedAs("Without the screen there is nothing to send the form through.")
                .contains("BedrockScreen.forServer(");
    }

    /**
     * Both Bedrock plugins have to load before this one.
     *
     * <p>{@code BedrockDetector.forServer} resolves once, while this plugin enables. It asks whether
     * {@code floodgate} is enabled and falls through to {@code Geyser-Spigot}, and whatever it decides then
     * is what the whole run uses. So a Floodgate that loads after us leaves Bedrock detection off until the
     * server is restarted, every Bedrock player gets a chest, and nothing anywhere says why.
     *
     * <p>Neither is required. A Java only server has neither installed, the detector answers none, and the
     * windows are chests for everybody, which is correct.
     */
    @Test
    @DisplayName("the descriptor makes both Bedrock plugins load before this one, and requires neither")
    void bothbedrockPluginsLoadFirst() throws IOException {
        String descriptor = Files.readString(DESCRIPTOR, StandardCharsets.UTF_8);

        for (String named : BEDROCK_PLUGINS) {
            assertThat(descriptor)
                    .describedAs("The detector is resolved once at enable, so " + named + " has to be up"
                            + " before this plugin is. Declare it under dependencies.server.")
                    .containsPattern(named + ":\\s*\\n\\s*load: BEFORE\\s*\\n\\s*required: false");
        }
    }

    /**
     * Whether this item becomes a form button: a row of a list, or a static item a click does something on.
     * A decorative pane is neither, and the engine leaves it out of the form rather than offering a tap
     * that does nothing.
     */
    private static boolean becomesAButton(MenuItemSpec item) {
        return item.list().isPresent() || item.click().hasAnyAction();
    }

    private static MenuSpec specOf(Path window) {
        try {
            return new MenuSpecLoader().load(window);
        } catch (Exception unreadable) {
            throw new IllegalStateException("the shipped window " + window + " does not parse", unreadable);
        }
    }

    /** Every window this plugin ships that the menu engine owns, read off the folder. */
    private static List<Path> windows() throws IOException {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(MENUS)) {
            for (Path file : files.sorted().toList()) {
                if (file.getFileName().toString().endsWith(".conf") && !isBespokeLayout(file)) {
                    found.add(file);
                }
            }
        }
        return found;
    }

    /**
     * Whether this file is one of the editor's own layouts rather than a menu engine window.
     *
     * <p>Told apart by its grammar: a bespoke layout places things by slot number and declares no items
     * block at all. A window that declares one is the engine's, whatever it is called.
     */
    private static boolean isBespokeLayout(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return !text.contains("items {") && text.contains("-slot");
    }
}
