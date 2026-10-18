package com.uxplima.blysis.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Something the site says every server may announce: a purchase, a top-up, a goal reached, a link made.
 *
 * <p>The fields are flattened to text, a nested object's under its dotted name ({@code player.name}), so an
 * operator's announcement reads them as placeholders without knowing the shape.
 */
public record SiteEvent(String id, String type, Map<String, String> fields) {

    public SiteEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        fields = Map.copyOf(fields);
    }
}
