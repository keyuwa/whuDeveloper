-- RA 揭榜模式 + 申请书 AI 辅助评分

ALTER TABLE tasks
    ADD COLUMN posting_mode VARCHAR(16) NOT NULL DEFAULT 'STANDARD' AFTER status,
    ADD COLUMN bid_deadline DATETIME NULL AFTER posting_mode,
    ADD COLUMN challenge_status VARCHAR(16) NULL AFTER bid_deadline;

UPDATE tasks
SET posting_mode = 'CHALLENGE',
    bid_deadline = CASE
        WHEN bid_deadline IS NULL AND status = 'OPEN' THEN DATE_ADD(NOW(), INTERVAL 30 DAY)
        ELSE bid_deadline
    END,
    challenge_status = CASE
        WHEN status = 'OPEN' THEN 'OPEN'
        WHEN status = 'CLOSED' THEN 'CLOSED'
        ELSE NULL
    END
WHERE role_type = 'RA';

UPDATE tasks
SET posting_mode = 'STANDARD',
    challenge_status = NULL
WHERE role_type <> 'RA';

ALTER TABLE applications
    ADD COLUMN ai_score INT NULL AFTER remind_count,
    ADD COLUMN ai_summary VARCHAR(500) NULL AFTER ai_score,
    ADD COLUMN ai_detail_json TEXT NULL AFTER ai_summary,
    ADD COLUMN ai_status VARCHAR(16) NOT NULL DEFAULT 'SKIPPED' AFTER ai_detail_json,
    ADD COLUMN ai_scored_at DATETIME NULL AFTER ai_status;
