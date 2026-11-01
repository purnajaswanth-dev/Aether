-- Migration V9: Atomic Action Requests and Idempotency
ALTER TABLE action_requests ADD COLUMN IF NOT EXISTS lock_version INT NOT NULL DEFAULT 1;
ALTER TABLE action_requests ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
ALTER TABLE action_requests ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;
ALTER TABLE action_requests ADD COLUMN IF NOT EXISTS executed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_action_requests_project_idempotency
    ON action_requests(project_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

