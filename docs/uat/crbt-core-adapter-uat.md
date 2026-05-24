# CRBT Core Adapter UAT Test Cases

## 1. Ringtone Assignment Submission
**Goal**: Verify users can submit ringtone assignment requests.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | POST `/ringtone-assignments` with valid `msisdn` and `ringtoneUrl`. | Returns 202 Accepted, assignment created with status `PENDING`. | |
| 1.2 | Check DB. | New `ringtone_assignments` record exists with correct userId, msisdn, ringtoneUrl. | |
| 1.3 | POST with blank `msisdn`. | Returns 400 Bad Request. | |
| 1.4 | POST with invalid `msisdn` format. | Returns 400 Bad Request. | |
| 1.5 | POST with blank `ringtoneUrl`. | Returns 400 Bad Request. | |

## 2. Async Mytone Sync Success
**Goal**: Verify successful assignment sync to Mytone CMS.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | Configure Mytone mock/API to return `success=true` with transaction ID. | Mytone client available. | |
| 2.2 | Submit a valid assignment. | API returns 202 immediately. | |
| 2.3 | Poll GET `/ringtone-assignments`. | Status transitions `PENDING` → `SYNCING` → `ACTIVE`. | |
| 2.4 | Verify final record. | `mytoneTransactionId` populated, `errorMessage` null. | |

## 3. Async Mytone Sync Failure
**Goal**: Verify failed Mytone sync is captured locally.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | Configure Mytone mock/API to return `success=false`. | Mytone returns error message. | |
| 3.2 | Submit a valid assignment. | API returns 202. | |
| 3.3 | Poll GET `/ringtone-assignments`. | Status becomes `FAILED`. | |
| 3.4 | Check assignment details. | `errorMessage` contains Mytone failure message, `retryCount` incremented. | |
| 3.5 | Repeat failures until retry count reaches 3. | Failure info is published to DLQ exchange. | |

## 4. Assignment Listing
**Goal**: Verify users can view their own assignments only.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | User A creates 2 assignments. | Both records saved. | |
| 4.2 | User B creates 1 assignment. | One separate record saved. | |
| 4.3 | User A calls GET `/ringtone-assignments`. | Response contains only User A's assignments sorted by `createdAt` desc. | |
| 4.4 | User B calls GET `/ringtone-assignments`. | Response contains only User B's assignment. | |

## 5. Remove Assignment
**Goal**: Verify ringtone assignment removal flow.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | Create an assignment and wait until status `ACTIVE`. | Assignment active in Mytone. | |
| 5.2 | DELETE `/ringtone-assignments/{assignmentId}` as owner. | Returns 200 OK with status `REMOVED`. | |
| 5.3 | Check Mytone mock/API. | DELETE `/ringtones/{msisdn}` called. | |
| 5.4 | DELETE another user's assignment. | Returns 403 Forbidden, code `COMMON_FORBIDDEN`. | |
| 5.5 | DELETE non-existent assignment ID. | Returns 404 Not Found, code `COMMON_NOT_FOUND`. | |

## 6. Mytone Unavailable / Circuit Breaker
**Goal**: Verify adapter handles external Mytone outage gracefully.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 6.1 | Stop Mytone mock/API or force timeout. | Mytone unavailable. | |
| 6.2 | Submit assignment. | API still returns 202 after local save. | |
| 6.3 | Poll assignment list. | Status becomes `FAILED`, `errorMessage` starts with `Mytone unavailable`. | |
| 6.4 | Restore Mytone and submit another assignment. | New assignment can become `ACTIVE` after circuit breaker recovers. | |

## 7. End-to-End CRBT Assignment Scenario
**Goal**: Verify full flow from generated/library ringtone to telco core assignment.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 7.1 | Select ringtone URL from community library or audio generation result. | Valid audio URL available. | |
| 7.2 | POST `/ringtone-assignments` with user's MSISDN and ringtone URL. | Assignment created as `PENDING`, 202 returned. | |
| 7.3 | Async worker transcodes media and calls Mytone CMS. | Status `SYNCING`. | |
| 7.4 | Mytone accepts assignment. | Status `ACTIVE`, transaction ID saved. | |
| 7.5 | User lists assignments. | Active assignment visible. | |
| 7.6 | User removes assignment. | Mytone deletion called, status `REMOVED`. | |
