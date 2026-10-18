package com.uxplima.blysis.application.port;

import java.util.List;
import java.util.Set;

import com.uxplima.blysis.domain.JobResult;

/** Where outcomes go: the site's {@code jobs/report}. */
@FunctionalInterface
public interface ResultSink {

    /**
     * Sends outcomes and answers the ids the site accepted.
     *
     * @throws SiteUnavailable when the site could not be reached or did not answer
     */
    Set<String> report(List<JobResult> results);
}
