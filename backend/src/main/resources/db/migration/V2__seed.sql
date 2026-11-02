-- Demo passwords (BCrypt): publisher / publisher123 , student / student123
INSERT INTO users (id, username, password_hash, display_name, role) VALUES
  (1, 'publisher', '$2b$10$uHYlnIqdWTJGqpD5UDdjoO6iYETL.N26HseHnQa0kxMQaQmAG3lJS', '李老师', 'PUBLISHER'),
  (2, 'student', '$2b$10$TGZuW/J5V9SuZEmiKLLU0.1COPYPmM64/j9oHfk566f9/qCZFepQ.', '王同学', 'STUDENT');

INSERT INTO tasks (id, title, description, type, status, publisher_id) VALUES
  (1, '数据结构课程助教', '协助《数据结构》课程完成答疑、作业批改和实验课辅导，每周约 6 小时。需要熟悉线性表、树和图，能把复杂概念讲清楚。', 'TA', 'OPEN', 1),
  (2, '校园开发者门户实习', '参与 WHU TOP Developer 门户的一个小功能开发，技术栈为 Vue 3。按周同步进展，结项时提交包含改动说明的 Pull Request 链接。', 'INTERN', 'OPEN', 1);
