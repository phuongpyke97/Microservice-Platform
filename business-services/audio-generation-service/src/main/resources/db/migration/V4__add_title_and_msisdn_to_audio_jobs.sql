ALTER TABLE audio_jobs ADD COLUMN title VARCHAR(255);
ALTER TABLE audio_jobs ADD COLUMN msisdn VARCHAR(50);

-- Backfill existing records with a default title from prompt or a generic string
UPDATE audio_jobs SET title = COALESCE(SUBSTRING(prompt FROM 1 FOR 35), 'DIY Ringback Tone') WHERE title IS NULL;
