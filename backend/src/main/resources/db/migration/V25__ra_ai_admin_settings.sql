INSERT INTO system_settings (setting_key, setting_value) VALUES
  ('ark_enabled', 'false'),
  ('ark_api_key', ''),
  ('ark_base_url', 'https://ark.cn-beijing.volces.com/api/v3'),
  ('ark_model', ''),
  ('ra_challenge_ai_prompt', '')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
