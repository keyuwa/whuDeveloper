SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS completions;
DROP TABLE IF EXISTS weekly_reports;
DROP TABLE IF EXISTS project_milestones;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS reminders;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS status_events;
DROP TABLE IF EXISTS applications;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS email_otps;
DROP TABLE IF EXISTS system_settings;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(120) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(32) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  real_name VARCHAR(64) NULL,
  ability_level VARCHAR(8) NULL,
  email_verified TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE email_otps (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(120) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  code_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  consumed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_email_otps_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  publisher_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  role_type VARCHAR(16) NOT NULL,
  difficulty VARCHAR(8) NOT NULL,
  background TEXT NOT NULL,
  tech_stack VARCHAR(300) NOT NULL,
  output_requirement TEXT NOT NULL,
  tech_requirement TEXT NOT NULL,
  duration_weeks INT NOT NULL,
  estimated_hours INT NOT NULL,
  repo_url VARCHAR(500) NULL,
  learning_materials TEXT NULL,
  stipend_text VARCHAR(500) NOT NULL,
  prd_md TEXT NOT NULL,
  tech_doc_md TEXT NULL,
  quota INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_tasks_publisher FOREIGN KEY (publisher_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE applications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  proposal_md TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  interview_at DATETIME(6) NULL,
  interview_note VARCHAR(500) NULL,
  review_note VARCHAR(500) NULL,
  remind_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_applications_task_student (task_id, student_id),
  CONSTRAINT fk_applications_task FOREIGN KEY (task_id) REFERENCES tasks (id),
  CONSTRAINT fk_applications_student FOREIGN KEY (student_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  body VARCHAR(2000) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_messages_thread (task_id, student_id),
  CONSTRAINT fk_messages_task FOREIGN KEY (task_id) REFERENCES tasks (id),
  CONSTRAINT fk_messages_student FOREIGN KEY (student_id) REFERENCES users (id),
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE projects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  application_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  publisher_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  enrolled_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_projects_application (application_id),
  CONSTRAINT fk_projects_application FOREIGN KEY (application_id) REFERENCES applications (id),
  CONSTRAINT fk_projects_task FOREIGN KEY (task_id) REFERENCES tasks (id),
  CONSTRAINT fk_projects_student FOREIGN KEY (student_id) REFERENCES users (id),
  CONSTRAINT fk_projects_publisher FOREIGN KEY (publisher_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_milestones (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  seq INT NOT NULL,
  title VARCHAR(100) NOT NULL,
  status VARCHAR(32) NOT NULL,
  note VARCHAR(1000) NULL,
  deliverable_url VARCHAR(500) NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_milestones_project_seq (project_id, seq),
  CONSTRAINT fk_milestones_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE weekly_reports (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  week_index INT NOT NULL,
  content TEXT NOT NULL,
  plan TEXT NULL,
  status VARCHAR(32) NOT NULL,
  score INT NULL,
  score_comment VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  scored_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reports_project_week (project_id, week_index),
  CONSTRAINT fk_reports_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE completions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  report_md TEXT NOT NULL,
  pr_urls TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  review_note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  reviewed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_completions_project (project_id),
  CONSTRAINT fk_completions_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  read_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_notifications_user (user_id),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reminders (
  id BIGINT NOT NULL AUTO_INCREMENT,
  type VARCHAR(32) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  dedupe_key VARCHAR(80) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_reminders_dedupe (dedupe_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE status_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_id BIGINT NULL,
  entity_type VARCHAR(32) NOT NULL,
  entity_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  detail VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_events_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE system_settings (
  setting_key VARCHAR(64) NOT NULL,
  setting_value VARCHAR(200) NOT NULL,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO users (id, email, password_hash, role, display_name, real_name, ability_level, email_verified, status) VALUES
  (1, 'admin@whu.edu.cn', '$2b$10$Md/KW1F45zZX7O64s9/WheMNPv0m0uxGK3NvXcvrW9MLDfxRrFSWq', 'ADMIN', '平台管理员', '平台管理员', NULL, 1, 'ACTIVE'),
  (2, 'li.teacher@cs.whu.edu.cn', '$2b$10$mjs632ZLZPEEpp7eaVjuw.Rbio6FQW0qLAeIiYVV9mCD5hYZRug3K', 'PUBLISHER', '李老师', '李明', NULL, 1, 'ACTIVE'),
  (3, 'wang.student@stu.edu.cn', '$2b$10$JfgPrL9Us4aXge9cZNPmauSSsZVAxvmBcrjikiSwaE1VV1KQ6/YdO', 'STUDENT', '王同学', NULL, 'L2', 1, 'ACTIVE');

INSERT INTO system_settings (setting_key, setting_value) VALUES
  ('review_sla_days', '7'),
  ('escalate_sla_days', '14');

INSERT INTO tasks (
  id, publisher_id, title, role_type, difficulty, background, tech_stack,
  output_requirement, tech_requirement, duration_weeks, estimated_hours, repo_url,
  learning_materials, stipend_text, prd_md, tech_doc_md, quota, status
) VALUES
  (1, 2, '数据结构课程助教', 'TA', 'L2',
   '协助《数据结构》完成答疑、作业批改和实验课辅导，让学生能独立完成线性表、树和图的实验。',
   'C/C++ · 数据结构',
   '实验课辅导记录、作业批改说明、常见问题文档。',
   '熟悉线性表、树、图；能把概念讲清楚。',
   16, 6, 'https://github.com/keyuwa/whuDeveloper',
   '教材《数据结构》对应章节；课程实验手册。',
   '按课程组助教标准发放，仅作展示，不在平台支付。',
   '## 背景\n课程需要固定时段助教。\n## 目标\n覆盖答疑与实验辅导。\n## 验收\n按周提交辅导记录。',
   '## 技术说明\n实验环境为课程机房的 C/C++。评分口径以课程组文档为准。',
   2, 'OPEN'),
  (2, 2, '校园开发者门户实习', 'INTERN', 'L3',
   '参与 WHU TOP Developer 门户的一个小功能，按周交付并在结项时附上 Pull Request。',
   'Vue 3 · TypeScript · Spring Boot',
   '可演示的功能增量，以及至少一条 PR 链接。',
   '能阅读现有前后端代码，按约定提交说明。',
   8, 12, 'https://github.com/keyuwa/whuDeveloper',
   '仓库 README；Vue 3 官方文档。',
   '实习津贴标准由实验室公布，本页只展示，不在线发放。',
   '## 背景\n门户需要持续补齐学生工作台。\n## 目标\n完成一个小功能并结项。\n## 验收\n周报 + PR。',
   '## 技术说明\n前端 Vue 3，接口 Spring Boot，数据 MySQL 8。',
   2, 'OPEN'),
  (3, 2, '操作系统实验研究助理', 'RA', 'L4',
   '协助整理操作系统实验的评测脚本与实验报告规范，服务后续课程迭代。',
   'Linux · Shell · Python',
   '评测脚本说明、样例报告模板。',
   '熟悉 Linux 基本命令，能写简单脚本。',
   12, 8, NULL,
   'OSTEP 相关章节。',
   '研究助理补贴按实验室公示，仅展示。',
   '## 背景\n实验评测需要可复用脚本。\n## 目标\n沉淀模板与说明。',
   NULL,
   1, 'OPEN');
