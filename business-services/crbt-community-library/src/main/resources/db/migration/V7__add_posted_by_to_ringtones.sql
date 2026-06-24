ALTER TABLE ringtones ADD COLUMN posted_by VARCHAR(50);
UPDATE ringtones SET posted_by = 'admin' WHERE posted_by IS NULL;
