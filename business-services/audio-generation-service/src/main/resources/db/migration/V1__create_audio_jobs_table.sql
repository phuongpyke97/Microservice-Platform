CREATE TABLE audio_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    prompt VARCHAR(500) NOT NULL,
    voice_id VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    result_url TEXT,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_audio_jobs_user_id ON audio_jobs(user_id);
CREATE INDEX idx_audio_jobs_status ON audio_jobs(status);
