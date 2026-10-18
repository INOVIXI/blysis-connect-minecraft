package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.blysis.paper.adapter.outbound.PluginSettings;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The small words a tile is drawn from, and the item a tile is drawn on.
 *
 * <p>A value goes into a tile as text and never as markup. The colour is the tile's business, so a word that
 * arrived already coloured would keep its own and lose the role the tile gives it.
 */
public final class Words {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Messages messages;
    private final Supplier<PluginSettings.Icons> icons;

    public Words(Messages messages, Supplier<PluginSettings.Icons> icons) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.icons = Objects.requireNonNull(icons, "icons");
    }

    /** One word of the catalogue, in the language of the player looking at it. */
    public String of(Player viewer, Keys key) {
        return PLAIN.serialize(messages.render(viewer, key));
    }

    /** The item the site named, when this server knows it as an item, or the file's choice for a shelf. */
    public String shelfIcon(@Nullable String named) {
        return itemOr(named, icons.get().shelf());
    }

    /** The item the site named, when this server knows it as an item, or the file's choice for a product. */
    public String productIcon(@Nullable String named) {
        return itemOr(named, icons.get().product());
    }

    private static String itemOr(@Nullable String named, String fallback) {
        if (named == null || named.isBlank()) {
            return fallback;
        }
        Material found = Material.matchMaterial(named.strip());
        return found == null || !found.isItem() || found.isAir() ? fallback : found.name();
    }
}
