ALTER TABLE users
  ADD COLUMN college VARCHAR(64) NULL AFTER real_name,
  ADD COLUMN major VARCHAR(64) NULL AFTER college,
  ADD COLUMN student_identity VARCHAR(16) NULL AFTER major,
  ADD COLUMN skills VARCHAR(500) NULL AFTER student_identity,
  ADD COLUMN bio TEXT NULL AFTER skills,
  ADD COLUMN resume_file_url VARCHAR(500) NULL AFTER bio,
  ADD COLUMN resume_file_name VARCHAR(255) NULL AFTER resume_file_url;
