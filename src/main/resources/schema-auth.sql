CREATE TABLE account_kind (
 user_id VARCHAR2(36 CHAR) PRIMARY KEY REFERENCES app_user(id),
 kind VARCHAR2(5 CHAR) NOT NULL CHECK(kind IN ('GUEST','HOST'))
);
CREATE TABLE social_identity (
 provider VARCHAR2(20 CHAR) NOT NULL, subject VARCHAR2(255 CHAR) NOT NULL,
 user_id VARCHAR2(36 CHAR) NOT NULL REFERENCES app_user(id), PRIMARY KEY(provider,subject)
);
CREATE TABLE guest_link (
 id VARCHAR2(36 CHAR) PRIMARY KEY, meeting_id VARCHAR2(36 CHAR) NOT NULL REFERENCES meeting(id),
 label VARCHAR2(30 CHAR) NOT NULL, token_hash VARCHAR2(64 CHAR) NOT NULL UNIQUE,
 guest_user_id VARCHAR2(36 CHAR) REFERENCES app_user(id), revoked NUMBER(1) DEFAULT 0 NOT NULL CHECK(revoked IN (0,1)),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL
);
CREATE INDEX guest_link_meeting_idx ON guest_link(meeting_id);
