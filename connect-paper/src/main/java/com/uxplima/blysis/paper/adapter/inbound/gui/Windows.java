package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecs;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The window files, read and opened. A window an operator deleted is a window this server does not have, and
 * a command that would open it says so in chat rather than drawing half of one.
 */
public final class Windows {

    private final Menus menus;
    private final Messages messages;
    private final Path dataFolder;
    private final Log log;
    private final List<String> read = new ArrayList<>();

    public Windows(Menus menus, Messages messages, Path dataFolder, Log log) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Reads {@code menus/<id>.conf}, or forgets the window when the file is gone. */
    public void read(String id, int rows) {
        String resource = "menus/" + id + ".conf";
        if (!Files.isRegularFile(dataFolder.resolve(resource))) {
            menus.unregisterSpec(id);
            read.remove(id);
            return;
        }
        menus.registerSpec(id, MenuSpecs.loadOrBundled(resource, dataFolder, rows, log));
        if (!read.contains(id)) {
            read.add(id);
        }
    }

    /** Opens a window on a subject, or says in chat that its file is gone. */
    public void open(Player viewer, String id, @Nullable Object subject) {
        if (menus.registeredSpec(id).isEmpty()) {
            messages.send(viewer, Keys.MENU_MISSING, Text.placeholder("menu", id));
            return;
        }
        menus.open(viewer, id, subject);
    }

    /** Asks yes or no in the library's confirm window. */
    public void confirm(Player viewer, Keys title, Runnable yes, Runnable no) {
        menus.confirm(viewer, messages.render(viewer, title), yes, no);
    }

    /** Every window read, for the check that every name a file writes is answered. */
    public List<MenuSpec> specs() {
        List<MenuSpec> found = new ArrayList<>();
        for (String id : read) {
            menus.registeredSpec(id).ifPresent(found::add);
        }
        return List.copyOf(found);
    }
}
