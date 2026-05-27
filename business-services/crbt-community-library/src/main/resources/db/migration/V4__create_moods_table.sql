-- Create moods table
CREATE TABLE moods (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

-- Seed default moods
INSERT INTO moods (name, description) VALUES
('Vui', 'Vui vẻ, sôi động, tích cực'),
('Buồn', 'Trầm lắng, u sầu, nhiều cảm xúc'),
('Chill', 'Thư giãn, nhẹ nhàng, êm dịu'),
('Hype', 'Hưng phấn, năng lượng cao'),
('Lãng mạn', 'Ngọt ngào, sâu lắng, tình cảm'),
('Thư giãn', 'Bình yên, thoải mái, giảm căng thẳng'),
('Năng động', 'Khỏe khoắn, tươi vui, nhiều năng lượng');

-- Alter ringtones table to add mood_id relationship
ALTER TABLE ringtones ADD COLUMN mood_id BIGINT;

-- Migrate existing mood VARCHAR data to mood_id
UPDATE ringtones SET mood_id = (SELECT id FROM moods WHERE name = 'Năng động') WHERE mood = 'Energetic';
UPDATE ringtones SET mood_id = (SELECT id FROM moods WHERE name = 'Vui') WHERE mood = 'Happy';
UPDATE ringtones SET mood_id = (SELECT id FROM moods WHERE name = 'Thư giãn') WHERE mood = 'Relaxing';
UPDATE ringtones SET mood_id = (SELECT id FROM moods WHERE name = 'Chill') WHERE mood = 'Calm';

-- Default fallback for any remaining rows
UPDATE ringtones SET mood_id = (SELECT id FROM moods WHERE name = 'Chill') WHERE mood_id IS NULL;

-- Make mood_id NOT NULL and add foreign key
ALTER TABLE ringtones ALTER COLUMN mood_id SET NOT NULL;
ALTER TABLE ringtones ADD CONSTRAINT fk_ringtones_mood_id FOREIGN KEY (mood_id) REFERENCES moods(id);

-- Drop legacy mood column
ALTER TABLE ringtones DROP COLUMN mood;

-- Update ringtone_deleted_history to store mood name
ALTER TABLE ringtone_deleted_history ADD COLUMN mood_name VARCHAR(50);
UPDATE ringtone_deleted_history SET mood_name = 'Chill' WHERE mood_name IS NULL;
ALTER TABLE ringtone_deleted_history ALTER COLUMN mood_name SET NOT NULL;

-- Create index for performance
CREATE INDEX idx_ringtones_mood_id ON ringtones(mood_id);
