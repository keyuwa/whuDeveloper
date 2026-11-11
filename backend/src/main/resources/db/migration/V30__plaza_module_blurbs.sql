-- 广场频道说明文案（管理侧可改）
INSERT INTO system_settings (setting_key, setting_value) VALUES
  ('plaza_blurb_ta', '助教任务：课程支持、答疑与教学协作'),
  ('plaza_blurb_intern', '开发任务：产品功能、工程实现与开源贡献'),
  ('plaza_blurb_ra', '科研任务：以揭榜形式开放方向，学生提交方案竞标'),
  ('plaza_blurb_team', '项目：运营、标注等协作需求，按岗位报名，一次可招多人')
ON DUPLICATE KEY UPDATE setting_value = setting_value;
