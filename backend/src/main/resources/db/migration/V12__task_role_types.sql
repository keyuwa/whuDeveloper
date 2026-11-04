UPDATE tasks
SET role_type = 'INTERN'
WHERE role_type IS NULL OR role_type = '' OR role_type = 'PROJECT';
