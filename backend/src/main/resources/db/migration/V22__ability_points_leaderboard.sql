ALTER TABLE users
  ADD COLUMN points INT NOT NULL DEFAULT 0 AFTER ability_level,
  ADD COLUMN ability_apprentice TINYINT(1) NOT NULL DEFAULT 0 AFTER points,
  ADD COLUMN ability_mgmt TINYINT(1) NOT NULL DEFAULT 0 AFTER ability_apprentice;

UPDATE users SET ability_level = 'LV1' WHERE ability_level = 'L1';
UPDATE users SET ability_level = 'LV2' WHERE ability_level = 'L2';
UPDATE users SET ability_level = 'LV3' WHERE ability_level = 'L3';
UPDATE users SET ability_level = 'LV4' WHERE ability_level = 'L4';
UPDATE users SET ability_level = 'LV5' WHERE ability_level = 'L5';
UPDATE users SET ability_level = 'LV0' WHERE role = 'STUDENT' AND (ability_level IS NULL OR ability_level = '');

UPDATE users SET points = CASE ability_level
  WHEN 'LV1' THEN 10
  WHEN 'LV2' THEN 50
  WHEN 'LV3' THEN 150
  WHEN 'LV4' THEN 300
  WHEN 'LV5' THEN 500
  ELSE 0
END WHERE role = 'STUDENT';

CREATE TABLE point_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  delta INT NOT NULL,
  balance_after INT NOT NULL,
  reason VARCHAR(200) NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  source_id VARCHAR(64) NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  operator_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_point_ledger_idem (idempotency_key),
  KEY idx_point_ledger_user (user_id),
  CONSTRAINT fk_point_ledger_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_point_ledger_operator FOREIGN KEY (operator_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ability_applications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  applicant_id BIGINT NOT NULL,
  target_level VARCHAR(8) NOT NULL,
  want_apprentice TINYINT(1) NOT NULL DEFAULT 0,
  want_mgmt TINYINT(1) NOT NULL DEFAULT 0,
  self_assessment TEXT NOT NULL,
  evidence TEXT NULL,
  status VARCHAR(32) NOT NULL,
  reviewer_id BIGINT NULL,
  result_level VARCHAR(8) NULL,
  result_apprentice TINYINT(1) NULL,
  result_mgmt TINYINT(1) NULL,
  decide_comment VARCHAR(1000) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  decided_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_ability_app_applicant (applicant_id),
  KEY idx_ability_app_status (status),
  CONSTRAINT fk_ability_app_applicant FOREIGN KEY (applicant_id) REFERENCES users (id),
  CONSTRAINT fk_ability_app_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
