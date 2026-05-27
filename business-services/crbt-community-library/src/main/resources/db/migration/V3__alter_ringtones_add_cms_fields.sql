-- Alter ringtones table to support CMS Admin fields
ALTER TABLE ringtones ADD COLUMN mood VARCHAR(100);
ALTER TABLE ringtones ADD COLUMN status BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE ringtones ADD COLUMN selection_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ringtones ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Update existing ringtones with a default mood
UPDATE ringtones SET mood = 'Energetic' WHERE category_id IN (3, 4); -- EDM, Rock
UPDATE ringtones SET mood = 'Happy' WHERE category_id = 5; -- V-Pop
UPDATE ringtones SET mood = 'Calm' WHERE category_id = 1; -- Pop
UPDATE ringtones SET mood = 'Relaxing' WHERE category_id = 4; -- Instrumental
UPDATE ringtones SET mood = 'Calm' WHERE mood IS NULL;

-- Create table to archive selection count statistics for deleted ringtones
CREATE TABLE ringtone_deleted_history (
    id BIGINT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    artist_name VARCHAR(100) NOT NULL,
    category_name VARCHAR(50) NOT NULL,
    selection_count BIGINT NOT NULL,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index columns for fast filtering and dashboard statistics
CREATE INDEX idx_ringtones_status ON ringtones(status);
CREATE INDEX idx_ringtones_deleted ON ringtones(deleted);
CREATE INDEX idx_ringtones_created_at ON ringtones(created_at);
CREATE INDEX idx_ringtones_selection_count ON ringtones(selection_count);
