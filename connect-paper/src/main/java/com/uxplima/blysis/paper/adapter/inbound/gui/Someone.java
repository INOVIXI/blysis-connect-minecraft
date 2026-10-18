package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.util.Objects;

/** A player online, offered as the one a gift is for. */
public record Someone(String name) {

    public Someone {
        Objects.requireNonNull(name, "name");
    }
}
