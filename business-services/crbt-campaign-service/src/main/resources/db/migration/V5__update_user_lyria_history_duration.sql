UPDATE user_lyria_history SET duration_seconds = 30 WHERE duration_seconds = 45;
ALTER TABLE user_lyria_history ALTER COLUMN duration_seconds SET DEFAULT 30;
