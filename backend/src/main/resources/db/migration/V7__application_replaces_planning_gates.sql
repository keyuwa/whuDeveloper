-- Preserve historical planning submissions without requiring repeated reviews.
UPDATE project_milestones m
JOIN projects p ON p.id = m.project_id
SET m.status = 'IN_PROGRESS'
WHERE m.seq = 3 AND m.status = 'PENDING' AND p.status = 'ACTIVE';
