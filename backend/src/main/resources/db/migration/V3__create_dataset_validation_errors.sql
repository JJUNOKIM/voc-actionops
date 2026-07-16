CREATE TABLE dataset_validation_errors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    csv_row_number INT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    error_code VARCHAR(50) NOT NULL,
    error_message VARCHAR(500) NOT NULL,
    raw_row_json JSON NOT NULL,
    created_at ${timestamp_type} NOT NULL,
    CONSTRAINT pk_dataset_validation_errors PRIMARY KEY (id),
    CONSTRAINT fk_dataset_validation_errors_dataset
        FOREIGN KEY (dataset_id) REFERENCES datasets (id),
    CONSTRAINT chk_dataset_validation_errors_row_number
        CHECK (csv_row_number >= 2)
);

CREATE INDEX idx_dataset_validation_errors_dataset_row
    ON dataset_validation_errors (dataset_id, csv_row_number);
