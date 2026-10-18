package com.uxplima.blysis.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.uxplima.blysis.application.port.Journal;
import com.uxplima.blysis.domain.JobResult;
import org.jspecify.annotations.Nullable;

/** A journal in memory, for the use-case tests. The SQLite one has its own test. */
final class MemoryJournal implements Journal {

    private final Map<String, @Nullable JobResult> rows = new LinkedHashMap<>();
    private final List<String> acknowledged = new ArrayList<>();

    @Override
    public boolean knows(String jobId) {
        return rows.containsKey(jobId);
    }

    @Override
    public void started(String jobId) {
        rows.put(jobId, null);
    }

    @Override
    public void finished(JobResult result) {
        rows.put(result.jobId(), result);
    }

    @Override
    public List<JobResult> unreported() {
        List<JobResult> out = new ArrayList<>();
        for (JobResult result : rows.values()) {
            if (result != null && !acknowledged.contains(result.jobId())) {
                out.add(result);
            }
        }
        return out;
    }

    @Override
    public List<String> unfinished() {
        List<String> out = new ArrayList<>();
        rows.forEach((id, result) -> {
            if (result == null) {
                out.add(id);
            }
        });
        return out;
    }

    @Override
    public void acknowledged(Collection<String> jobIds) {
        acknowledged.addAll(jobIds);
    }

    @Nullable JobResult resultOf(String jobId) {
        return rows.get(jobId);
    }
}
