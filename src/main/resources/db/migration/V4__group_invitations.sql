CREATE TABLE group_invite (
 id VARCHAR(36) PRIMARY KEY,
 meeting_id VARCHAR(36) NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 revoked SMALLINT NOT NULL DEFAULT 0 CHECK(revoked IN (0,1)),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX group_invite_meeting_idx ON group_invite(meeting_id);
