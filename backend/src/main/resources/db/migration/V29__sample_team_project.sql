-- 样例：项目频道（role_type=TEAM）— 一任务多岗位
INSERT INTO tasks (
  publisher_id, title, role_type, role_serial, public_code, tech_field, difficulty,
  background, tech_stack, programming_languages, support_languages,
  output_requirement, tech_requirement, duration_weeks, schedule_type, education_levels,
  estimated_hours, repo_url, learning_materials, stipend_text,
  prd_md, tech_doc_md, quota, status, posting_mode, bid_deadline, challenge_status
)
SELECT
  2,
  '校园物资中转站协作',
  'TEAM',
  1,
  'TEAM-0001',
  '运营',
  'L3',
  '实验室物资中转站需要同时推进系统迭代与日常运营：研发同学完善出入库与调度功能，运营同学负责现场对接、单据核对与周报汇总。一次发布多个岗位，按岗申请、按岗计名额。',
  '按岗协作',
  '不限',
  '中文',
  '按所选岗位完成约定工作：研发交付可用功能增量与周报；运营提交值班记录、异常说明与周汇总。',
  '具备对应岗位所需基础能力，能按时参与协作并按周汇报进展。',
  16,
  NULL,
  NULL,
  8,
  NULL,
  '中转站操作手册（入职后发放）；简易出入库流程说明。',
  '按实验室公示标准发放，仅作展示，不在平台支付。',
  '## 背景\n中转站事务跨研发与运营，需要按岗位分别招人。\n## 目标\n研发稳住系统；运营稳住现场与单据。\n## 申请说明\n请选择对应岗位后提交申请，说明可投入时段与相关经验。',
  NULL,
  23,
  'OPEN',
  'STANDARD',
  NULL,
  NULL
WHERE NOT EXISTS (
  SELECT 1 FROM tasks WHERE public_code = 'TEAM-0001'
);

INSERT INTO task_positions (task_id, title, direction, quota, requirement, sort_order)
SELECT t.id, '研发', '研发', 3, '熟悉 Web 前后端其一即可；能阅读现有出入库相关代码并按周提交进展。', 10
FROM tasks t
WHERE t.public_code = 'TEAM-0001'
  AND NOT EXISTS (
    SELECT 1 FROM task_positions p WHERE p.task_id = t.id AND p.direction = '研发'
  );

INSERT INTO task_positions (task_id, title, direction, quota, requirement, sort_order)
SELECT t.id, '运营', '运营', 20, '认真细致，能完成现场对接、单据核对与周报；无需编程基础。', 20
FROM tasks t
WHERE t.public_code = 'TEAM-0001'
  AND NOT EXISTS (
    SELECT 1 FROM task_positions p WHERE p.task_id = t.id AND p.direction = '运营'
  );
