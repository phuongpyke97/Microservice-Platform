CREATE TABLE user_lyria_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    msisdn VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(50) NOT NULL,
    mood VARCHAR(50) NOT NULL,
    instrument VARCHAR(50),
    audio_url VARCHAR(500) NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 45,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_lyria_history_user_id ON user_lyria_history(user_id);
CREATE INDEX idx_user_lyria_history_deleted ON user_lyria_history(deleted);
