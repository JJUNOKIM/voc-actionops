CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    expires_at ${timestamp_type} NOT NULL,
    used_at ${timestamp_type} NULL,
    revoked_at ${timestamp_type} NULL,
    replaced_by_token_id BIGINT NULL,
    created_at ${timestamp_type} NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_replacement
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_refresh_tokens_expiration
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_tokens_family
    ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_expires_at
    ON refresh_tokens (user_id, expires_at);
