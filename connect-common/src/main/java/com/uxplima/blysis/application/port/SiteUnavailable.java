package com.uxplima.blysis.application.port;

import java.io.Serial;

/** The site could not be reached, or answered in a way that means "not now". */
public final class SiteUnavailable extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SiteUnavailable(String reason, Throwable cause) {
        super(reason, cause);
    }

    public SiteUnavailable(String reason) {
        super(reason);
    }
}
