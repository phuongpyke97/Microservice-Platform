# CRBT Community Library API Documentation

## Overview
`crbt-community-library` provides a collection of ringtones (ringtones) categorized by genre or theme. Users can search for music, browse categories, and fetch random ringtones for discovery.

**Base URL**: `/library` (Routed through API Gateway: `http://localhost:8080/library`)

---

## 1. Get All Categories
Retrieves a list of all available music categories (e.g., Pop, Rock, Lo-fi).

- **URL**: `/categories`
- **Method**: `GET`
- **Auth Required**: No

### Success Response
- **Code**: `200 OK`
- **Body**:
```json
{
  "code": "SUCCESS",
  "message": "SUCCESS",
  "data": [
    {
      "id": 1,
      "name": "Pop",
      "description": "Popular music",
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "timestamp": "2024-01-01T12:00:00Z"
}
```

---

## 2. Search Ringtones
Searches for ringtones with filtering and pagination.

- **URL**: `/ringtones/search`
- **Method**: `GET`
- **Auth Required**: No

### Query Parameters
- `q` (string, optional): Search query (matches title or artist).
- `categoryId` (long, optional): Filter by category ID.
- `featured` (boolean, optional): Filter featured ringtones.
- `page` (int, default 0): Page number.
- `size` (int, default 20): Page size.

### Success Response
- **Code**: `200 OK`
- **Body**: Standard `PageResponse` containing the list of `RingtoneResponse`.

---

## 3. Get Random Ringtone
Fetches a random ringtone, optionally filtered by genre.

- **URL**: `/ringtones/random`
- **Method**: `GET`
- **Auth Required**: No

### Query Parameters
- `genre` (string, optional): Specific genre to pick from.

### Success Response
- **Code**: `200 OK`
- **Body**: `RingtoneResponse` object.

---

## 4. Create Category (Admin Only)
- **URL**: `/categories`
- **Method**: `POST`
- **Request Body**: `CategoryRequest` (name, description)

---

## 5. Create Ringtone (Admin Only)
- **URL**: `/ringtones`
- **Method**: `POST`
- **Request Body**:
```json
{
  "title": "Summer Vibe",
  "artistName": "AI Artist",
  "audioUrl": "http://minio:9000/audio/uuid-file.mp3",
  "coverImageUrl": "http://minio:9000/image/uuid-cover.png",
  "durationSeconds": 30,
  "featured": true,
  "categoryId": 1
}
```

---

## Ringtone Object Structure
```json
{
  "id": 1,
  "title": "Song Title",
  "artistName": "Artist Name",
  "audioUrl": "...",
  "coverImageUrl": "...",
  "durationSeconds": 30,
  "featured": true,
  "category": { ...CategoryObject... },
  "createdAt": "...",
  "updatedAt": "..."
}
```