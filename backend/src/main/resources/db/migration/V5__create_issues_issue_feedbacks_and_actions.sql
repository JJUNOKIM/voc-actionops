CREATE TABLE issues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARCHAR(100) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    priority_score DECIMAL(5, 2) NULL,
    status VARCHAR(30) NOT NULL,
    assignee_id BIGINT NULL,
    first_seen_at ${timestamp_type} NULL,
    last_seen_at ${timestamp_type} NULL,
    created_at ${timestamp_type} NOT NULL,
    updated_at ${timestamp_type} NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_issues PRIMARY KEY (id),
    CONSTRAINT fk_issues_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_issues_assignee
        FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT chk_issues_priority
        CHECK (priority IN ('P0', 'P1', 'P2', 'P3')),
    CONSTRAINT chk_issues_priority_score
        CHECK (priority_score IS NULL OR (priority_score >= 0 AND priority_score <= 100)),
    CONSTRAINT chk_issues_status
        CHECK (status IN ('NEW', 'TRIAGED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'MONITORING', 'CLOSED')),
    CONSTRAINT chk_issues_assignee_required
        CHECK (status IN ('NEW', 'TRIAGED') OR assignee_id IS NOT NULL),
    CONSTRAINT chk_issues_seen_range
        CHECK (first_seen_at IS NULL OR last_seen_at IS NULL OR first_seen_at <= last_seen_at)
);

CREATE INDEX idx_issues_organization_created_at
    ON issues (organization_id, created_at);
CREATE INDEX idx_issues_organization_status
    ON issues (organization_id, status);
CREATE INDEX idx_issues_organization_priority
    ON issues (organization_id, priority);
CREATE INDEX idx_issues_organization_category
    ON issues (organization_id, category);
CREATE INDEX idx_issues_assignee_status
    ON issues (assignee_id, status);

CREATE TABLE issue_feedbacks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    feedback_id BIGINT NOT NULL,
    similarity_score DECIMAL(5, 4) NULL,
    is_representative BOOLEAN NOT NULL DEFAULT FALSE,
    linked_by VARCHAR(20) NOT NULL,
    created_at ${timestamp_type} NOT NULL,
    CONSTRAINT pk_issue_feedbacks PRIMARY KEY (id),
    CONSTRAINT uk_issue_feedbacks_issue_feedback UNIQUE (issue_id, feedback_id),
    CONSTRAINT fk_issue_feedbacks_issue
        FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_issue_feedbacks_feedback
        FOREIGN KEY (feedback_id) REFERENCES feedbacks (id),
    CONSTRAINT chk_issue_feedbacks_similarity
        CHECK (similarity_score IS NULL OR (similarity_score >= 0 AND similarity_score <= 1)),
    CONSTRAINT chk_issue_feedbacks_linked_by
        CHECK (linked_by IN ('AI', 'MANUAL'))
);

CREATE INDEX idx_issue_feedbacks_feedback
    ON issue_feedbacks (feedback_id);
CREATE INDEX idx_issue_feedbacks_issue_representative
    ON issue_feedbacks (issue_id, is_representative);

CREATE TABLE actions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    issue_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(30) NOT NULL,
    assignee_id BIGINT NULL,
    due_date DATE NULL,
    created_at ${timestamp_type} NOT NULL,
    updated_at ${timestamp_type} NOT NULL,
    completed_at ${timestamp_type} NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_actions PRIMARY KEY (id),
    CONSTRAINT fk_actions_issue
        FOREIGN KEY (issue_id) REFERENCES issues (id),
    CONSTRAINT fk_actions_assignee
        FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT chk_actions_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELED')),
    CONSTRAINT chk_actions_completed_at
        CHECK ((status = 'DONE' AND completed_at IS NOT NULL)
            OR (status <> 'DONE' AND completed_at IS NULL))
);

CREATE INDEX idx_actions_issue_status
    ON actions (issue_id, status);
CREATE INDEX idx_actions_assignee_status
    ON actions (assignee_id, status);
CREATE INDEX idx_actions_due_date
    ON actions (due_date);
