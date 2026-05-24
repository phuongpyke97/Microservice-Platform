# CRBT Community Library Specification

## 1. Overview
`crbt-community-library` manages the shared ringtone catalog. It serves as a fallback and discovery source for ringtones when AI generation is unavailable or users prefer pre-made content.

## 2. Technical Stack
- **Framework**: Spring Boot 3.2.x, Java 21
- **Database**: PostgreSQL 16
- **Cache**: Spring Cache (`@Cacheable`) for ringtone search results
- **Port**: 8091

## 3. Database Schema

### `categories` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Category ID |
| `name` | VARCHAR(50) | NOT NULL, UNIQUE | Category name |
| `description` | VARCHAR(200) | | Category description |
| `created_at` | TIMESTAMP | NOT NULL | Creation time |
| `updated_at` | TIMESTAMP | | Last update time |

### `ringtones` table
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY | Ringtone ID |
| `title` | VARCHAR(150) | NOT NULL | Ringtone title |
| `artist_name` | VARCHAR(100) | NOT NULL | Artist/creator name |
| `audio_url` | VARCHAR(500) | NOT NULL | Audio file URL |
| `cover_image_url` | VARCHAR(500) | | Cover image URL |
| `duration_seconds` | INT | NOT NULL | Ringtone duration |
| `featured` | BOOLEAN | NOT NULL | Featured flag |
| `category_id` | BIGINT | FK (categories.id) | Category reference |
| `created_at` | TIMESTAMP | NOT NULL | Creation time |
| `updated_at` | TIMESTAMP | | Last update time |

## 4. Business Logic Flows

### 4.1. Category Management
1. Admin creates a category with name and description.
2. Category name must be unique.
3. Users can retrieve all categories without authentication.

### 4.2. Ringtone Management
1. Admin creates a ringtone linked to an existing category.
2. Required metadata: title, artist, audio URL, duration, category.
3. Optional metadata: cover image, featured flag.
4. If the category does not exist, the service returns `COMMON_NOT_FOUND`.

### 4.3. Search Flow
1. User submits optional filters: query text, category ID, featured flag.
2. Service builds a JPA Specification dynamically.
3. Query text matches both `title` and `artistName` using case-insensitive LIKE.
4. Results are sorted by `createdAt` descending.
5. Results are wrapped in `PageResponse`.
6. Search results are cached by query, categoryId, featured, page, and size.

### 4.4. Random Ringtone Flow
1. User requests a random ringtone, optionally by genre.
2. If genre is provided, service tries genre-specific random selection first.
3. If no ringtone exists for the genre, service falls back to global random.
4. If no ringtone exists at all, returns `COMMON_NOT_FOUND`.

## 5. Caching Strategy
- Search endpoint is cached using Spring Cache.
- Cache key includes: query, categoryId, featured, page number, page size.
- Cache region: `ringtones`.

## 6. Integration Points
- **file-service**: Audio and cover URLs should point to files managed by file-service/MinIO.
- **audio-generation-service**: Generated audio can be inserted into the library for reuse.
- **crbt-campaign-service**: Can use library as fallback when AI generation fails.