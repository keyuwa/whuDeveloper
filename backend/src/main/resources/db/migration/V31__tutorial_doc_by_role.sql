-- Split tutorial links for student vs publisher; seed student from legacy key when present.
-- Derived table: MySQL 8.4 rejects system_settings.setting_value in this INSERT ... SELECT
-- ON DUPLICATE KEY UPDATE as ambiguous when the source is the same table.
INSERT INTO system_settings (setting_key, setting_value)
SELECT 'tutorial_doc_url_student', src.setting_value
FROM (
  SELECT setting_value
  FROM system_settings
  WHERE setting_key = 'tutorial_doc_url'
) AS src
ON DUPLICATE KEY UPDATE setting_value = IF(
  system_settings.setting_value IS NULL OR system_settings.setting_value = '',
  src.setting_value,
  system_settings.setting_value
);

INSERT INTO system_settings (setting_key, setting_value) VALUES
  ('tutorial_doc_url_student', ''),
  ('tutorial_doc_url_publisher', '')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
