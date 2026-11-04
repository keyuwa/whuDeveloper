CREATE TABLE task_shortlists (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_shortlist_student_task (student_id, task_id),
  CONSTRAINT fk_shortlist_student FOREIGN KEY (student_id) REFERENCES users (id),
  CONSTRAINT fk_shortlist_task FOREIGN KEY (task_id) REFERENCES tasks (id)
);
