ALTER TABLE weekly_reports
  ADD COLUMN blockers TEXT NULL AFTER content,
  ADD COLUMN support_needed TEXT NULL AFTER plan;
