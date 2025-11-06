-- V1__init.sql
-- Base schema for IP Blocklist + auditing

-- NOTE: The database (schema) must exist before running Flyway.
-- Example: CREATE DATABASE ip_blocklist CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 1) Slack users (identity source)
CREATE TABLE IF NOT EXISTS slack_users (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  slack_user_id   VARCHAR(32) NOT NULL UNIQUE,
  team_id         VARCHAR(32) NULL,
  display_name    VARCHAR(255) NOT NULL,
  real_name       VARCHAR(255) NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_slack_users_team ON slack_users(team_id);

-- 2) Managed IP addresses
-- We store the IP as text (45) and also a generated binary column (ip_bin)
-- to ensure uniqueness and correct searches for IPv4/IPv6.
CREATE TABLE IF NOT EXISTS ip_addresses (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  ip               VARCHAR(45) NOT NULL,
  -- Normalized binary representation (IPv4 mapped/IPv6), prevents duplicates due to formatting
  ip_bin           VARBINARY(16) GENERATED ALWAYS AS (INET6_ATON(ip)) STORED,
  reason           VARCHAR(512) NULL,
  active           TINYINT(1) NOT NULL DEFAULT 1,
  created_by       BIGINT NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deactivated_by   BIGINT NULL,
  deactivated_at   TIMESTAMP NULL DEFAULT NULL,

  CONSTRAINT uq_ip_bin UNIQUE (ip_bin),
  CONSTRAINT fk_ip_created_by     FOREIGN KEY (created_by)     REFERENCES slack_users(id),
  CONSTRAINT fk_ip_deactivated_by FOREIGN KEY (deactivated_by) REFERENCES slack_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ip_active ON ip_addresses(active);
CREATE INDEX idx_ip_updated_at ON ip_addresses(updated_at);

-- 3) Change audit (full history)
CREATE TABLE IF NOT EXISTS ip_audit_log (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  ip_id          BIGINT NOT NULL,
  action         ENUM('CREATE','UPDATE','DEACTIVATE','REACTIVATE') NOT NULL,
  actor_user_id  BIGINT NOT NULL,
  prev_value     JSON NULL,   -- previous state (relevant parts, e.g. {"reason":"...","active":1})
  new_value      JSON NULL,   -- new state
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_audit_ip   FOREIGN KEY (ip_id)         REFERENCES ip_addresses(id),
  CONSTRAINT fk_audit_user FOREIGN KEY (actor_user_id)  REFERENCES slack_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_ip_id ON ip_audit_log(ip_id);
CREATE INDEX idx_audit_actor ON ip_audit_log(actor_user_id);
CREATE INDEX idx_audit_created_at ON ip_audit_log(created_at);

-- 4) Optional view to export only active IPs (useful for local debugging)
--    *The endpoint will use the service/query, but this view helps manual inspection.
DROP VIEW IF EXISTS v_active_ips;
CREATE VIEW v_active_ips AS
  SELECT ip FROM ip_addresses WHERE active = 1 ORDER BY ip;
