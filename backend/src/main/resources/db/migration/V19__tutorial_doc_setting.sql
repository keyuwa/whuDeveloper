INSERT INTO system_settings (setting_key, setting_value) VALUES
  ('tutorial_doc_url', 'https://my.feishu.cn/wiki/TTGHwEm5JifVMHkStujcTa1KnMh?from=from_copylink'),
  ('tutorial_banner_text', '系统使用教程请参考平台文档，点击查看完整说明。')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);
