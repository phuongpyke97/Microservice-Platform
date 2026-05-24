CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE TABLE ringtones (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    artist_name VARCHAR(100) NOT NULL,
    audio_url VARCHAR(500) NOT NULL,
    cover_image_url VARCHAR(500),
    duration_seconds INT NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_ringtones_category_id ON ringtones(category_id);
CREATE INDEX idx_ringtones_title ON ringtones(title);
CREATE INDEX idx_ringtones_artist_name ON ringtones(artist_name);
CREATE INDEX idx_ringtones_featured ON ringtones(featured);
