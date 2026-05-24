-- Seed categories
INSERT INTO categories (name, description) VALUES
('Pop', 'Popular mainstream music'),
('Rock', 'Energetic rock anthems'),
('EDM', 'Electronic Dance Music for clubs'),
('Instrumental', 'Piano and acoustic guitar solos'),
('V-Pop', 'Vietnamese popular songs');

-- Seed sample ringtones (placeholder URLs)
INSERT INTO ringtones (title, artist_name, audio_url, duration_seconds, featured, category_id) VALUES
('Lạc Trôi', 'Sơn Tùng M-TP', 'http://minio:9000/audio/lac-troi.mp3', 30, true, 5),
('Waiting For You', 'MONO', 'http://minio:9000/audio/waiting-for-you.mp3', 30, true, 5),
('Animals', 'Martin Garrix', 'http://minio:9000/audio/animals.mp3', 30, false, 3),
('Hotel California', 'Eagles', 'http://minio:9000/audio/hotel-california.mp3', 30, false, 2),
('River Flows In You', 'Yiruma', 'http://minio:9000/audio/river-flows.mp3', 30, true, 4);
