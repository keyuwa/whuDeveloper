UPDATE system_settings
SET setting_value = '难题：运营、标注等协作需求，按岗位报名，一次可招多人'
WHERE setting_key = 'plaza_blurb_team'
  AND setting_value = '项目：运营、标注等协作需求，按岗位报名，一次可招多人';
