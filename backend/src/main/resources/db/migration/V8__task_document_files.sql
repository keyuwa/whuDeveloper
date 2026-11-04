ALTER TABLE tasks
  ADD COLUMN prd_file_url VARCHAR(500) NULL AFTER prd_md,
  ADD COLUMN prd_file_name VARCHAR(255) NULL AFTER prd_file_url,
  ADD COLUMN tech_doc_file_url VARCHAR(500) NULL AFTER tech_doc_md,
  ADD COLUMN tech_doc_file_name VARCHAR(255) NULL AFTER tech_doc_file_url,
  MODIFY COLUMN prd_md TEXT NULL;
