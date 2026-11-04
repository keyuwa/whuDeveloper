ALTER TABLE system_settings
  MODIFY COLUMN setting_value MEDIUMTEXT NOT NULL;

INSERT INTO system_settings (setting_key, setting_value) VALUES
  ('ability_rubric_md', '')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
