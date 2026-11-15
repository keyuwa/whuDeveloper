UPDATE system_settings
SET setting_value = CONCAT('难题', SUBSTRING(setting_value, 3))
WHERE setting_key = 'plaza_blurb_team'
  AND setting_value LIKE '项目%';
