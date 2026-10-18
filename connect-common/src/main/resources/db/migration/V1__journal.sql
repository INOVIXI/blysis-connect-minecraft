-- What this server did with each job the site handed it. A row is written before the command runs, so a
-- job id found here is never run again, whatever the site offers.
CREATE TABLE blysisconnect_journal (
    job_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    started_at      BIGINT       NOT NULL,
    outcome         VARCHAR(16),
    detail          VARCHAR(512),
    finished_at     BIGINT,
    acknowledged_at BIGINT
);

CREATE INDEX blysisconnect_journal_unreported ON blysisconnect_journal (acknowledged_at, finished_at);
