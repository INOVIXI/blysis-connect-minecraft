package com.uxplima.blysis.application.port;

import java.util.Collection;
import java.util.List;

import com.uxplima.blysis.domain.JobResult;

/**
 * What this server did with each job, kept on its own disk so a crash cannot make it run one twice.
 *
 * <p>A job is written {@code started} before its command runs, given an outcome after, and marked
 * acknowledged once the site has the outcome. A job the journal knows is never run again.
 */
public interface Journal {

    /** True when this job id was started here before, whatever happened next. */
    boolean knows(String jobId);

    /** Records that the job is about to run. */
    void started(String jobId);

    /** Records what happened. */
    void finished(JobResult result);

    /** Outcomes the site has not acknowledged yet, oldest first. */
    List<JobResult> unreported();

    /** Jobs that were started and never given an outcome: the process stopped while they ran. */
    List<String> unfinished();

    /** Records that the site has these outcomes. */
    void acknowledged(Collection<String> jobIds);
}
