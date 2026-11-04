-- 将旧版含糊节点名迁移为大厂常见的阶段门名称。
UPDATE project_milestones SET title = '需求确认' WHERE title = '对齐启动';
UPDATE project_milestones SET title = '设计评审' WHERE title = '迭代开发';
UPDATE project_milestones SET title = '开发交付' WHERE title = '结项验收';
