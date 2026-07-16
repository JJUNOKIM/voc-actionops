CREATE TABLE datasets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    file_url VARCHAR(500) NULL,
    column_mapping_json JSON NULL,
    status VARCHAR(30) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    valid_count INT NOT NULL DEFAULT 0,
    invalid_count INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at ${timestamp_type} NOT NULL,
    updated_at ${timestamp_type} NOT NULL,
    CONSTRAINT pk_datasets PRIMARY KEY (id),
    CONSTRAINT fk_datasets_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_datasets_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_datasets_counts_non_negative
        CHECK (total_count >= 0 AND valid_count >= 0 AND invalid_count >= 0),
    CONSTRAINT chk_datasets_count_balance
        CHECK (valid_count + invalid_count <= total_count)
);

CREATE INDEX idx_datasets_organization_created_at
    ON datasets (organization_id, created_at);
CREATE INDEX idx_datasets_organization_status
    ON datasets (organization_id, status);
CREATE INDEX idx_datasets_organization_source_type
    ON datasets (organization_id, source_type);
CREATE INDEX idx_datasets_created_by ON datasets (created_by);

CREATE TABLE feedbacks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    external_id VARCHAR(150) NULL,
    source_type VARCHAR(50) NOT NULL,
    customer_segment VARCHAR(100) NULL,
    product_name VARCHAR(150) NULL,
    rating DECIMAL(3, 1) NULL,
    content TEXT NOT NULL,
    language VARCHAR(20) NULL,
    feedback_created_at ${timestamp_type} NULL,
    ingested_at ${timestamp_type} NOT NULL,
    CONSTRAINT pk_feedbacks PRIMARY KEY (id),
    CONSTRAINT uk_feedbacks_dataset_external_id UNIQUE (dataset_id, external_id),
    CONSTRAINT fk_feedbacks_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_feedbacks_dataset
        FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT chk_feedbacks_rating
        CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
);

CREATE INDEX idx_feedbacks_organization_ingested_at
    ON feedbacks (organization_id, ingested_at);
CREATE INDEX idx_feedbacks_organization_dataset
    ON feedbacks (organization_id, dataset_id);
CREATE INDEX idx_feedbacks_organization_source_type
    ON feedbacks (organization_id, source_type);
