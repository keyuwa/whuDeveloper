-- Do not ship a baked-in ARK base URL; admin must configure Agent Plan / platform endpoint explicitly.
UPDATE system_settings
SET setting_value = ''
WHERE setting_key = 'ark_base_url'
  AND setting_value IN (
      'https://ark.cn-beijing.volces.com/api/v3',
      'https://ark.cn-beijing.volces.com/api/plan/v1'
  );
