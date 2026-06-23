-- Flyway migration: Add alert_sent column to lyria_daily_stats table
ALTER TABLE lyria_daily_stats ADD COLUMN alert_sent BOOLEAN NOT NULL DEFAULT FALSE;
