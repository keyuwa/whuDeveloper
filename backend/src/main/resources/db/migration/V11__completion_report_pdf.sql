ALTER TABLE completions
  ADD COLUMN report_file_url VARCHAR(500) NULL AFTER report_md,
  ADD COLUMN report_file_name VARCHAR(255) NULL AFTER report_file_url,
  MODIFY COLUMN report_md TEXT NULL;
