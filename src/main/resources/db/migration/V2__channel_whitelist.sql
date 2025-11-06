-- V2__channel_whitelist.sql
-- Whitelist of Slack channels allowed to run /ip commands

CREATE TABLE IF NOT EXISTS slack_channel_whitelist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel_id   VARCHAR(32)  NOT NULL UNIQUE,
  channel_name VARCHAR(255) NULL,
  team_id      VARCHAR(32)  NULL,
  active       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Optional seed/example (remove in prod)
-- INSERT IGNORE INTO slack_channel_whitelist (channel_id, channel_name, team_id)
-- VALUES ('C01ABCDEF12', 'ip-admin', 'T001');
