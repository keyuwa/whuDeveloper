-- Split tutorial links for student vs publisher; seed student from legacy key when present.
INSERT INTO system_settings (setting_key, setting_value)
SELECT 'tutorial_doc_url_student', setting_value
FROM system_settings
WHERE setting_key = 'tutorial_doc_url'
ON DUPLICATE KEY UPDATE setting_value = IF(
  system_settings.setting_value IS NULL OR system_settings.setting_value = '',
  VALUES(setting_value),
  system_settings.setting_value
);

INSERT INTO system_settings (setting_key, setting_value) VALUES
  ('tutorial_doc_url_student', ''),
  ('tutorial_doc_url_publisher', '')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
