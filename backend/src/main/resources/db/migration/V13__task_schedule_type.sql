ALTER TABLE tasks
  ADD COLUMN schedule_type VARCHAR(32) NULL AFTER duration_weeks;
