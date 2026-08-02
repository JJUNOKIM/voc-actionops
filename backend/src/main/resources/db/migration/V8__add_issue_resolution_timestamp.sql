ALTER TABLE issues
    ADD COLUMN resolved_at ${timestamp_type} NULL;

UPDATE issues
SET resolved_at = updated_at
WHERE status IN ('RESOLVED', 'MONITORING', 'CLOSED');

ALTER TABLE issues
    ADD CONSTRAINT chk_issues_resolved_at
        CHECK (
            (status IN ('RESOLVED', 'MONITORING', 'CLOSED') AND resolved_at IS NOT NULL)
            OR (status NOT IN ('RESOLVED', 'MONITORING', 'CLOSED') AND resolved_at IS NULL)
        );

CREATE INDEX idx_issues_organization_resolved_at
    ON issues (organization_id, resolved_at);
