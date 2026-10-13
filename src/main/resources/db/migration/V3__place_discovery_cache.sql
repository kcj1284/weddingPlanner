CREATE TABLE place_discovery_cache (
 cache_key VARCHAR(64) PRIMARY KEY,
 kind VARCHAR(10) NOT NULL,
 query_text VARCHAR(300) NOT NULL,
 place_type VARCHAR(3) NOT NULL,
 payload TEXT NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 accessed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX place_discovery_refresh_idx ON place_discovery_cache(expires_at,accessed_at);
