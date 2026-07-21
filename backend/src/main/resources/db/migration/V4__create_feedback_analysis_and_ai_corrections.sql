CREATE TABLE feedback_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feedback_id BIGINT NOT NULL,
    sentiment VARCHAR(20) NULL,
    sentiment_score DECIMAL(6, 5) NULL,
    category VARCHAR(100) NULL,
    urgency_score DECIMAL(5, 4) NULL,
    summary VARCHAR(1000) NULL,
    confidence_score DECIMAL(5, 4) NULL,
    model_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000) NULL,
    analyzed_at ${timestamp_type} NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_feedback_analysis PRIMARY KEY (id),
    CONSTRAINT uk_feedback_analysis_feedback UNIQUE (feedback_id),
    CONSTRAINT fk_feedback_analysis_feedback
        FOREIGN KEY (feedback_id) REFERENCES feedbacks (id),
    CONSTRAINT chk_feedback_analysis_sentiment_score
        CHECK (sentiment_score IS NULL OR (sentiment_score >= -1 AND sentiment_score <= 1)),
    CONSTRAINT chk_feedback_analysis_urgency_score
        CHECK (urgency_score IS NULL OR (urgency_score >= 0 AND urgency_score <= 1)),
    CONSTRAINT chk_feedback_analysis_confidence_score
        CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1)),
    CONSTRAINT chk_feedback_analysis_status_payload
        CHECK (
            (status = 'PENDING'
                AND sentiment IS NULL
                AND sentiment_score IS NULL
                AND category IS NULL
                AND urgency_score IS NULL
                AND summary IS NULL
                AND confidence_score IS NULL
                AND error_message IS NULL
                AND analyzed_at IS NULL)
            OR
            (status = 'SUCCESS'
                AND sentiment IS NOT NULL
                AND sentiment_score IS NOT NULL
                AND category IS NOT NULL
                AND urgency_score IS NOT NULL
                AND summary IS NOT NULL
                AND confidence_score IS NOT NULL
                AND error_message IS NULL
                AND analyzed_at IS NOT NULL)
            OR
            (status = 'FAILED'
                AND sentiment IS NULL
                AND sentiment_score IS NULL
                AND category IS NULL
                AND urgency_score IS NULL
                AND summary IS NULL
                AND confidence_score IS NULL
                AND error_message IS NOT NULL
                AND analyzed_at IS NOT NULL)
        )
);

CREATE TABLE ai_corrections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feedback_id BIGINT NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    ai_value VARCHAR(1000) NOT NULL,
    corrected_value VARCHAR(1000) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    corrected_by BIGINT NOT NULL,
    created_at ${timestamp_type} NOT NULL,
    CONSTRAINT pk_ai_corrections PRIMARY KEY (id),
    CONSTRAINT fk_ai_corrections_feedback
        FOREIGN KEY (feedback_id) REFERENCES feedbacks (id),
    CONSTRAINT fk_ai_corrections_corrected_by
        FOREIGN KEY (corrected_by) REFERENCES users (id)
);

CREATE INDEX idx_ai_corrections_feedback_created_at
    ON ai_corrections (feedback_id, created_at);
CREATE INDEX idx_ai_corrections_corrected_by
    ON ai_corrections (corrected_by);
