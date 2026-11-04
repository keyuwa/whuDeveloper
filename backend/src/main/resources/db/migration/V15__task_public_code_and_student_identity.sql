ALTER TABLE tasks
  ADD COLUMN role_serial INT NULL AFTER role_type,
  ADD COLUMN public_code VARCHAR(32) NULL AFTER role_serial;

UPDATE tasks t
JOIN (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY role_type ORDER BY id) AS rn
  FROM tasks
) numbered ON numbered.id = t.id
SET t.role_serial = numbered.rn,
    t.public_code = CONCAT(
      CASE t.role_type
        WHEN 'TA' THEN 'TA'
        WHEN 'RA' THEN 'RA'
        ELSE 'DEV'
      END,
      '-',
      LPAD(numbered.rn, 4, '0')
    );

UPDATE tasks
SET education_levels = TRIM(BOTH ',' FROM REPLACE(REPLACE(REPLACE(
        CONCAT(',', REPLACE(education_levels, '，', ','), ','),
        ',本科,', ',本科生,'),
        ',硕士,', ',硕士生,'),
        ',博士,', ',博士生,'))
WHERE education_levels IS NOT NULL AND education_levels <> '';

ALTER TABLE tasks
  MODIFY COLUMN role_serial INT NOT NULL,
  MODIFY COLUMN public_code VARCHAR(32) NOT NULL;

CREATE UNIQUE INDEX uk_tasks_role_serial ON tasks (role_type, role_serial);
CREATE UNIQUE INDEX uk_tasks_public_code ON tasks (public_code);
