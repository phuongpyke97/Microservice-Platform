-- Update existing categories to match UI screen
UPDATE categories SET name = 'Tình cảm', description = 'Nhạc tình cảm, sâu lắng' WHERE name = 'Rock';
UPDATE categories SET name = 'Hài hước', description = 'Nhạc hài hước, vui nhộn' WHERE name = 'Instrumental';
UPDATE categories SET name = 'Thiếu nhi', description = 'Nhạc thiếu nhi, vui tươi' WHERE name = 'V-Pop';

-- Insert new categories that do not exist yet
INSERT INTO categories (name, description) VALUES
('Chill', 'Nhạc nhẹ nhàng, thư giãn'),
('Classical', 'Nhạc cổ điển, giao hưởng');
