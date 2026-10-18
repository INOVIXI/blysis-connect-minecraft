package com.uxplima.blysis.domain;

import java.util.List;
import java.util.Objects;

/** What one long-poll brought back: the jobs it claimed, the events after the cursor, and the new cursor. */
public record Poll(List<Job> jobs, List<SiteEvent> events, String eventCursor, String catalogVersion) {

    public Poll {
        jobs = List.copyOf(jobs);
        events = List.copyOf(events);
        Objects.requireNonNull(eventCursor, "eventCursor");
        Objects.requireNonNull(catalogVersion, "catalogVersion");
    }
}
