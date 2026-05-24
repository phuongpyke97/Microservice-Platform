# CRBT Community Library UAT Test Cases

## 1. Category Management
**Goal**: Verify that categories can be created and retrieved.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | POST `/library/categories` with valid name and description. | Returns 200 OK, category is saved to DB. | |
| 1.2 | GET `/library/categories`. | Returns 200 OK with the created category in the list. | |
| 1.3 | POST `/library/categories` with an existing name. | Returns error, constraint violation on DB level. | |

## 2. Ringtone Creation
**Goal**: Verify that ringtones can be created under valid categories.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | POST `/library/ringtones` with valid category ID. | Returns 200 OK, ringtone is saved. | |
| 2.2 | POST `/library/ringtones` with non-existent category ID. | Returns 404 Not Found, code `COMMON_NOT_FOUND`. | |

## 3. Search Flow
**Goal**: Verify that ringtones can be searched and filtered.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | GET `/library/ringtones/search?q=test` | Returns 200 OK, list contains ringtones matching 'test' in title or artist. | |
| 3.2 | GET `/library/ringtones/search?categoryId=1` | Returns 200 OK, list only contains ringtones from category 1. | |
| 3.3 | GET `/library/ringtones/search?featured=true` | Returns 200 OK, list only contains featured ringtones. | |
| 3.4 | GET `/library/ringtones/search` (no filters). | Returns 200 OK, paginated list of all ringtones sorted by `createdAt` desc. | |

## 4. Random Discovery
**Goal**: Verify that users can fetch random ringtones.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | GET `/library/ringtones/random` without genre. | Returns 200 OK, single random ringtone. | |
| 4.2 | GET `/library/ringtones/random?genre=Pop`. | Returns 200 OK, single random ringtone from 'Pop' category (if exists) or fallback global random. | |
| 4.3 | GET `/library/ringtones/random` on empty database. | Returns 404 Not Found, code `COMMON_NOT_FOUND`. | |

## 5. End-to-End Search Scenario
**Goal**: Verify the caching behavior of the search endpoint.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | Search for `q=summer`. | Returns 200 OK, DB query executes. | |
| 5.2 | Search for `q=summer` again immediately. | Returns 200 OK, result returned from cache without DB hit. | |
| 5.3 | Create a new ringtone matching 'summer'. | Returns 200 OK, ringtone saved. | |
| 5.4 | Search for `q=summer` again. | Cached result is returned (note: cache invalidation strategy might need review if immediate visibility is required). | |