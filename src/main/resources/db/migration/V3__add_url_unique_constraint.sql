-- V3__add_url_unique_constraint.sql
-- url_indicators lacked a uniqueness guard (unlike ip_bin / hash_norm / domain_norm),
-- which allowed duplicate URLs and could make UrlRepository.findByUrlValue (a Mono)
-- fail whenever more than one row matched.
--
-- url_value is TEXT, so a prefix-length unique index is used. With utf8mb4 on InnoDB
-- (DYNAMIC row format, MySQL 8.0+) the maximum index prefix is 3072 bytes = 768 chars.
-- URLs longer than 768 chars are still stored in full; only the first 768 chars are
-- used for the uniqueness check.
--
-- NOTE: if the table already contains duplicate url_value rows, deduplicate them
-- before applying this migration or it will fail.
ALTER TABLE url_indicators
  ADD UNIQUE INDEX uq_url_value (url_value(768));
