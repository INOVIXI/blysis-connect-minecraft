package com.uxplima.blysis.application.port;

import java.util.List;

import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.SiteEvent;

/** What the connection tells the rest of the plugin: its state, the site's events and a changed catalogue. */
public interface LinkWatcher {

    /** The state changed. Called once per change, never once per attempt. */
    void stateChanged(LinkState state, String detail);

    /** The site answered hello. */
    default void greeted(Hello hello) {}

    /** Events arrived, oldest first. */
    default void events(List<SiteEvent> events) {}

    /** The site's catalogue is not the one cached here any more. */
    default void catalogChanged(String version) {}
}
