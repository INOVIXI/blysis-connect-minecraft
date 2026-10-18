package com.uxplima.blysis.application.port;

import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.Poll;
import com.uxplima.blysis.domain.StatusPush;
import org.jspecify.annotations.Nullable;

/**
 * The site's delivery side, as this server calls it. Every call blocks the calling thread, which is never a
 * server thread, and ends in an answer, a {@link SiteRefused}, or a {@link SiteUnavailable}.
 */
public interface Site extends ResultSink {

    /** Makes this process the token's current one. */
    Hello hello(String software, String softwareVersion, String role);

    /** Holds until the site has jobs or events for this server, or its hold time ends. */
    Poll poll(@Nullable String eventCursor);

    /** Says who is online here. */
    void status(StatusPush push);
}
