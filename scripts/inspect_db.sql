SELECT current_database() AS database_name, current_user AS connected_user;
SHOW transaction_read_only;
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
SELECT 'app_user' AS table_name, COUNT(*) AS row_count FROM app_user
UNION ALL SELECT 'meeting', COUNT(*) FROM meeting
UNION ALL SELECT 'member', COUNT(*) FROM member
UNION ALL SELECT 'poll_option', COUNT(*) FROM poll_option
UNION ALL SELECT 'vote', COUNT(*) FROM vote;
