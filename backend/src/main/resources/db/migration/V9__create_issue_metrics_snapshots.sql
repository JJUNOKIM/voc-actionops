CREATE TABLE issue_metrics_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    feedback_count BIGINT NOT NULL,
    analyzed_feedback_count BIGINT NOT NULL,
    negative_feedback_count BIGINT NOT NULL,
    average_sentiment_score DECIMAL(6, 5) NULL,
    average_urgency_score DECIMAL(5, 4) NULL,
    priority_score DECIMAL(5, 2) NULL,
    unresolved_action_count BIGINT NOT NULL,
    created_at ${timestamp_type} NOT NULL,
    updated_at ${timestamp_type} NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_issue_metrics_snapshots PRIMARY KEY (id),
    CONSTRAINT uk_issue_metrics_snapshots_issue_date UNIQUE (issue_id, snapshot_date),
    CONSTRAINT fk_issue_metrics_snapshots_issue
        FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT chk_issue_metrics_snapshots_counts
        CHECK (feedback_count >= 0
            AND analyzed_feedback_count >= 0
            AND negative_feedback_count >= 0
            AND unresolved_action_count >= 0
            AND analyzed_feedback_count <= feedback_count
            AND negative_feedback_count <= analyzed_feedback_count),
    CONSTRAINT chk_issue_metrics_snapshots_sentiment
        CHECK (average_sentiment_score IS NULL
            OR (average_sentiment_score >= -1 AND average_sentiment_score <= 1)),
    CONSTRAINT chk_issue_metrics_snapshots_urgency
        CHECK (average_urgency_score IS NULL
            OR (average_urgency_score >= 0 AND average_urgency_score <= 1)),
    CONSTRAINT chk_issue_metrics_snapshots_priority
        CHECK (priority_score IS NULL OR (priority_score >= 0 AND priority_score <= 100))
);

CREATE INDEX idx_issue_metrics_snapshots_date
    ON issue_metrics_snapshots (snapshot_date);
