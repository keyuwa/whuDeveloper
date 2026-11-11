-- 项目频道岗位 + 申请关联岗位；TEAM_POSITION 字典
CREATE TABLE IF NOT EXISTS task_positions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  title VARCHAR(64) NOT NULL,
  direction VARCHAR(64) NOT NULL,
  quota INT NOT NULL,
  requirement TEXT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_task_positions_task (task_id),
  CONSTRAINT fk_task_positions_task FOREIGN KEY (task_id) REFERENCES tasks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @has_position_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'applications'
    AND COLUMN_NAME = 'position_id'
);
SET @sql := IF(
  @has_position_id = 0,
  'ALTER TABLE applications ADD COLUMN position_id BIGINT NULL AFTER task_id, ADD KEY idx_applications_position (position_id), ADD CONSTRAINT fk_applications_position FOREIGN KEY (position_id) REFERENCES task_positions (id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO dict_items (dict_type, item_value, item_label, sort_order, enabled)
SELECT * FROM (
  SELECT 'TEAM_POSITION' AS dict_type, '研发' AS item_value, '研发' AS item_label, 10 AS sort_order, 1 AS enabled
  UNION ALL SELECT 'TEAM_POSITION', '运营', '运营', 20, 1
  UNION ALL SELECT 'TEAM_POSITION', '数据采集', '数据采集', 30, 1
  UNION ALL SELECT 'TEAM_POSITION', '数据标注', '数据标注', 40, 1
  UNION ALL SELECT 'TEAM_POSITION', '质检', '质检', 50, 1
  UNION ALL SELECT 'TEAM_POSITION', '其它', '其它', 60, 1
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM dict_items d
  WHERE d.dict_type = seed.dict_type AND d.item_value = seed.item_value
);
