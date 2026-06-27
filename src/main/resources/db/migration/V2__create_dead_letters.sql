CREATE TABLE dead_letters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL,
    provider VARCHAR(100) NOT NULL,
    retry_count INT NOT NULL,
    request_payload JSONB NOT NULL,
    error_message TEXT,
    dead_lettered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dead_letters_job_id ON dead_letters (job_id);
CREATE INDEX idx_dead_letters_provider ON dead_letters (provider);
CREATE INDEX idx_dead_letters_created ON dead_letters (dead_lettered_at);
