ALTER TABLE app_user ADD COLUMN auth_version INTEGER NOT NULL DEFAULT 0;
CREATE TABLE recovery_contact (
 user_id VARCHAR(36) PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 email VARCHAR(254) NOT NULL UNIQUE, verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE recovery_token (
 token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 purpose VARCHAR(12) NOT NULL CHECK(purpose IN ('VERIFY','RESET')),
 email VARCHAR(254) NOT NULL, expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ
);
CREATE INDEX recovery_token_user ON recovery_token(user_id,purpose);
CREATE TABLE recovery_request_limit (
 bucket VARCHAR(64) PRIMARY KEY, attempts INTEGER NOT NULL, expires_at TIMESTAMPTZ NOT NULL
);
