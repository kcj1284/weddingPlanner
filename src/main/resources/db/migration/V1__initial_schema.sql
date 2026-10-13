CREATE TABLE app_user (
 id VARCHAR(36) PRIMARY KEY, username VARCHAR(30) NOT NULL UNIQUE,
 display_name VARCHAR(30) NOT NULL, password_hash VARCHAR(100) NOT NULL
);
CREATE TABLE meeting (
 id VARCHAR(36) PRIMARY KEY, host_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
 title VARCHAR(80) NOT NULL, description VARCHAR(500), area VARCHAR(80) NOT NULL,
 expected_count SMALLINT NOT NULL CHECK(expected_count BETWEEN 2 AND 100),
 invite_token VARCHAR(64) NOT NULL UNIQUE,
 status VARCHAR(12) DEFAULT 'OPEN' NOT NULL CHECK(status IN ('OPEN','CONFIRMED')),
 final_time_id VARCHAR(36), final_place_id VARCHAR(36), created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE member (
 meeting_id VARCHAR(36) NOT NULL REFERENCES meeting(id),
 user_id VARCHAR(36) NOT NULL REFERENCES app_user(id), PRIMARY KEY(meeting_id,user_id)
);
CREATE TABLE poll_option (
 id VARCHAR(36) PRIMARY KEY, meeting_id VARCHAR(36) NOT NULL REFERENCES meeting(id),
 kind VARCHAR(5) NOT NULL CHECK(kind IN ('TIME','PLACE')), title VARCHAR(120) NOT NULL,
 detail VARCHAR(300), map_url VARCHAR(500), phone VARCHAR(40),
 reservation VARCHAR(12) DEFAULT 'UNKNOWN' NOT NULL CHECK(reservation IN ('UNKNOWN','AVAILABLE','BOOKED')),
 reservation_note VARCHAR(300), created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE vote (
 meeting_id VARCHAR(36) NOT NULL, user_id VARCHAR(36) NOT NULL,
 option_id VARCHAR(36) NOT NULL REFERENCES poll_option(id),
 PRIMARY KEY(meeting_id,user_id,option_id), FOREIGN KEY(meeting_id,user_id) REFERENCES member(meeting_id,user_id)
);
CREATE INDEX option_meeting_idx ON poll_option(meeting_id);
CREATE INDEX member_user_idx ON member(user_id);
CREATE TABLE account_kind (
 user_id VARCHAR(36) PRIMARY KEY REFERENCES app_user(id),
 kind VARCHAR(5) NOT NULL CHECK(kind IN ('GUEST','HOST'))
);
CREATE TABLE social_identity (
 provider VARCHAR(20) NOT NULL, subject VARCHAR(255) NOT NULL,
 user_id VARCHAR(36) NOT NULL REFERENCES app_user(id), PRIMARY KEY(provider,subject)
);
CREATE TABLE guest_link (
 id VARCHAR(36) PRIMARY KEY, meeting_id VARCHAR(36) NOT NULL REFERENCES meeting(id),
 label VARCHAR(30) NOT NULL, token_hash VARCHAR(64) NOT NULL UNIQUE,
 guest_user_id VARCHAR(36) REFERENCES app_user(id), revoked SMALLINT DEFAULT 0 NOT NULL CHECK(revoked IN (0,1)),
 created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL, expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX guest_link_meeting_idx ON guest_link(meeting_id);
