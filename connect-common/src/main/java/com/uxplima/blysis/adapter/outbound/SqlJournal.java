package com.uxplima.blysis.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.blysis.application.port.Journal;
import com.uxplima.blysis.domain.JobResult;
import com.uxplima.blysis.domain.Outcome;
import com.uxplima.uxmlib.storage.migration.MigrationResources;
import com.uxplima.uxmlib.storage.migration.MigrationRunner;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import com.uxplima.uxmlib.storage.sql.StatementBinder;

/**
 * The journal in a SQLite file of this server's own.
 *
 * <p>Every write is a single statement on its own connection, so it is on disk before the call returns and
 * before the command it guards runs. An acknowledged row is kept for a while, so a job the site offers
 * again after a lost answer is still recognised, and then swept.
 */
public final class SqlJournal implements Journal {

    /** Where the migrations are in the jar. */
    public static final String MIGRATIONS = "db/migration";

    /** The table that records which migrations ran. */
    public static final String HISTORY = "blysisconnect_schema_history";

    /** How many outcomes one report carries at most. */
    private static final int REPORT_BATCH = 100;

    /** Longer than any lease the site gives, so an offered-again job is always recognised. */
    private static final Duration KEPT = Duration.ofDays(7);

    private static final int DETAIL_LENGTH = 512;

    private final Sql sql;
    private final Clock clock;

    private SqlJournal(Sql sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    /** Brings the table up to date and returns a journal on it. */
    public static SqlJournal open(Database database, Clock clock) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(clock, "clock");
        new MigrationRunner(database, HISTORY)
                .apply(MigrationResources.loadRequired(SqlJournal.class.getClassLoader(), MIGRATIONS));
        return new SqlJournal(new Sql(database), clock);
    }

    @Override
    public boolean knows(String jobId) {
        Objects.requireNonNull(jobId, "jobId");
        return sql.queryFirst(
                        "SELECT 1 FROM blysisconnect_journal WHERE job_id = ?",
                        statement -> statement.setString(1, jobId),
                        row -> Boolean.TRUE)
                .isPresent();
    }

    @Override
    public void started(String jobId) {
        Objects.requireNonNull(jobId, "jobId");
        sql.update("INSERT INTO blysisconnect_journal (job_id, started_at) VALUES (?, ?)", statement -> {
            statement.setString(1, jobId);
            statement.setLong(2, clock.millis());
        });
    }

    @Override
    public void finished(JobResult result) {
        Objects.requireNonNull(result, "result");
        String detail = result.detail();
        String kept = detail == null || detail.length() <= DETAIL_LENGTH ? detail : detail.substring(0, DETAIL_LENGTH);
        sql.update(
                "UPDATE blysisconnect_journal SET outcome = ?, detail = ?, finished_at = ? WHERE job_id = ?",
                statement -> {
                    statement.setString(1, result.outcome().wire());
                    statement.setString(2, kept);
                    statement.setLong(3, clock.millis());
                    statement.setString(4, result.jobId());
                });
    }

    @Override
    public List<JobResult> unreported() {
        return sql.query(
                "SELECT job_id, outcome, detail FROM blysisconnect_journal"
                        + " WHERE acknowledged_at IS NULL AND outcome IS NOT NULL ORDER BY finished_at LIMIT ?",
                statement -> statement.setInt(1, REPORT_BATCH),
                row -> new JobResult(
                        row.getString("job_id"), outcomeOf(row.getString("outcome")), row.getString("detail")));
    }

    @Override
    public List<String> unfinished() {
        return sql.query(
                "SELECT job_id FROM blysisconnect_journal WHERE outcome IS NULL ORDER BY started_at",
                statement -> {},
                row -> row.getString("job_id"));
    }

    @Override
    public void acknowledged(Collection<String> jobIds) {
        Objects.requireNonNull(jobIds, "jobIds");
        if (jobIds.isEmpty()) {
            return;
        }
        long now = clock.millis();
        List<String> ids = new ArrayList<>(jobIds);
        sql.batch(
                "UPDATE blysisconnect_journal SET acknowledged_at = ? WHERE job_id = ? AND acknowledged_at IS NULL",
                ids.stream()
                        .<StatementBinder>map(id -> statement -> {
                            statement.setLong(1, now);
                            statement.setString(2, id);
                        })
                        .toList());
    }

    /** How many outcomes the site has not acknowledged, for an operator. */
    public int unreportedCount() {
        return sql.queryFirst(
                        "SELECT COUNT(*) AS waiting FROM blysisconnect_journal"
                                + " WHERE acknowledged_at IS NULL AND outcome IS NOT NULL",
                        statement -> {},
                        row -> row.getInt("waiting"))
                .orElse(0);
    }

    /** Removes acknowledged rows old enough that the site can no longer offer their job again. */
    public int sweep() {
        long before = clock.millis() - KEPT.toMillis();
        return sql.update(
                "DELETE FROM blysisconnect_journal WHERE acknowledged_at IS NOT NULL AND acknowledged_at < ?",
                statement -> statement.setLong(1, before));
    }

    private static Outcome outcomeOf(String wire) {
        for (Outcome outcome : Outcome.values()) {
            if (outcome.wire().equals(wire)) {
                return outcome;
            }
        }
        throw new IllegalStateException(
                "the journal holds an outcome this version does not know: " + wire.toLowerCase(Locale.ROOT));
    }
}
