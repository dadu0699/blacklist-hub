-- V2__add_ioc_tables.sql
-- Adds support for HASH, DOMAIN, URL and makes the audit log polymorphic.

-- 1) Table for HASH Indicators
CREATE TABLE IF NOT EXISTS hash_indicators (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  hash_value       VARCHAR(64) NOT NULL, -- Assuming SHA-256
  hash_norm        VARCHAR(64) GENERATED ALWAYS AS (LOWER(hash_value)) STORED,
  reason           VARCHAR(512) NULL,
  active           TINYINT(1) NOT NULL DEFAULT 1,
  created_by       BIGINT NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deactivated_by   BIGINT NULL,
  deactivated_at   TIMESTAMP NULL DEFAULT NULL,
  
  CONSTRAINT uq_hash_norm UNIQUE (hash_norm),
  CONSTRAINT fk_hash_created_by     FOREIGN KEY (created_by)     REFERENCES slack_users(id),
  CONSTRAINT fk_hash_deactivated_by FOREIGN KEY (deactivated_by) REFERENCES slack_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) Table for DOMAIN Indicators
CREATE TABLE IF NOT EXISTS domain_indicators (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_name      VARCHAR(255) NOT NULL,
  domain_norm      VARCHAR(255) GENERATED ALWAYS AS (LOWER(domain_name)) STORED,
  reason           VARCHAR(512) NULL,
  active           TINYINT(1) NOT NULL DEFAULT 1,
  created_by       BIGINT NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deactivated_by   BIGINT NULL,
  deactivated_at   TIMESTAMP NULL DEFAULT NULL,
  
  CONSTRAINT uq_domain_norm UNIQUE (domain_norm),
  CONSTRAINT fk_domain_created_by     FOREIGN KEY (created_by)     REFERENCES slack_users(id),
  CONSTRAINT fk_domain_deactivated_by FOREIGN KEY (deactivated_by) REFERENCES slack_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) Table for URL Indicators
CREATE TABLE IF NOT EXISTS url_indicators (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  url_value        TEXT NOT NULL, -- URLs can be very long
  reason           VARCHAR(512) NULL,
  active           TINYINT(1) NOT NULL DEFAULT 1,
  created_by       BIGINT NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  deactivated_by   BIGINT NULL,
  deactivated_at   TIMESTAMP NULL DEFAULT NULL,
  
  CONSTRAINT fk_url_created_by     FOREIGN KEY (created_by)     REFERENCES slack_users(id),
  CONSTRAINT fk_url_deactivated_by FOREIGN KEY (deactivated_by) REFERENCES slack_users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 4) Modify the audit table to be polymorphic
-- First, rename the existing table
RENAME TABLE ip_audit_log TO ioc_audit_log;

-- Second, modify its structure
ALTER TABLE ioc_audit_log
    
    -- 1. Drop the original FK that pointed ONLY to ip_addresses
    DROP FOREIGN KEY fk_audit_ip,
    
    -- 2. Drop the old index
    DROP INDEX idx_audit_ip_id,
    
    -- 3. Rename the ip_id column -> indicator_id
    CHANGE COLUMN ip_id indicator_id BIGINT NOT NULL,
    
    -- 4. Add the 'ioc_type' column (the "discriminator") after the id
    ADD COLUMN ioc_type ENUM('IP', 'HASH', 'DOMAIN', 'URL') NOT NULL AFTER id;

-- 5. Create the new polymorphic index
CREATE INDEX idx_audit_ioc ON ioc_audit_log(ioc_type, indicator_id);