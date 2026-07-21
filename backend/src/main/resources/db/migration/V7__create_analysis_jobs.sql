CREATE TABLE analysis_jobs (
    id VARCHAR(36) NOT NULL,
    organization_id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count INT NOT NULL,
    processed_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000) NULL,
    started_at ${timestamp_type} NULL,
    completed_at ${timestamp_type} NULL,
    created_at ${timestamp_type} NOT NULL,
    updated_at ${timestamp_type} NOT NULL,
    CONSTRAINT pk_analysis_jobs PRIMARY KEY (id),
    CONSTRAINT fk_analysis_jobs_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_analysis_jobs_dataset
        FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT chk_analysis_jobs_counts
        CHECK (
            total_count >= 0
            AND processed_count >= 0
            AND success_count >= 0
            AND failed_count >= 0
            AND processed_count = success_count + failed_count
            AND processed_count <= total_count
        ),
    CONSTRAINT chk_analysis_jobs_status
        CHECK (
            (status = 'PENDING' AND started_at IS NULL AND completed_at IS NULL)
            OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
            OR (status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS')
                AND processed_count = total_count
                AND completed_at IS NOT NULL)
            OR (status = 'FAILED'
                AND failure_reason IS NOT NULL
                AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_analysis_jobs_dataset_created_at
    ON analysis_jobs (dataset_id, created_at);
CREATE INDEX idx_analysis_jobs_status
    ON analysis_jobs (status);

CREATE TABLE analysis_job_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id VARCHAR(36) NOT NULL,
    feedback_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    created_at ${timestamp_type} NOT NULL,
    updated_at ${timestamp_type} NOT NULL,
    CONSTRAINT pk_analysis_job_items PRIMARY KEY (id),
    CONSTRAINT uk_analysis_job_items_job_feedback UNIQUE (job_id, feedback_id),
    CONSTRAINT fk_analysis_job_items_job
        FOREIGN KEY (job_id) REFERENCES analysis_jobs (id),
    CONSTRAINT fk_analysis_job_items_feedback
        FOREIGN KEY (feedback_id) REFERENCES feedbacks (id),
    CONSTRAINT chk_analysis_job_items_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_analysis_job_items_status
        CHECK (
            (status = 'PENDING')
            OR (status = 'RUNNING' AND attempt_count > 0)
            OR (status = 'SUCCESS' AND attempt_count > 0 AND last_error IS NULL)
            OR (status = 'FAILED' AND attempt_count > 0 AND last_error IS NOT NULL)
        )
);

CREATE INDEX idx_analysis_job_items_job_status_id
    ON analysis_job_items (job_id, status, id);
