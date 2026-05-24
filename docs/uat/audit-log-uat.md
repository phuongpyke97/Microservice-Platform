# Audit Log Service UAT Test Cases

## 1. Event-Driven Record Creation
**Goal**: Verify audit logs are recorded automatically from security events.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | Trigger a login action in `auth-service`. | `auth-service` publishes `AuditLogEvent` to RabbitMQ. | |
| 1.2 | Check `audit-log-service` DB. | New record created in `audit_logs` with action=`LOGIN`, status=`SUCCESS`, correct userId, sourceIp, and timestamp. | |
| 1.3 | Trigger a failed login (wrong password). | New record created with action=`LOGIN`, status=`FAILED`. | |
| 1.4 | Trigger a payment action. | New record created with action=`PAYMENT`. | |
| 1.5 | Attempt to UPDATE or DELETE a record directly. | No such API exposed; operation not possible via service. | |

## 2. Query Audit Logs — Basic
**Goal**: Verify administrators can retrieve audit logs.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | GET `/audit/query`. | Returns 200 OK, paginated list of all audit logs sorted by timestamp desc. | |
| 2.2 | Verify pagination. | Response includes `page`, `size`, `totalElements`, `totalPages`. | |
| 2.3 | GET `/audit/query` on empty database. | Returns 200 OK, empty `content` list, `totalElements: 0`. | |

## 3. Query Audit Logs — Filters
**Goal**: Verify filter parameters work correctly.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | GET `/audit/query?userId=42`. | Returns only logs where `userId=42`. | |
| 3.2 | GET `/audit/query?action=LOGIN`. | Returns only logs where `action=LOGIN`. | |
| 3.3 | GET `/audit/query?status=FAILED`. | Returns only logs where `status=FAILED`. | |
| 3.4 | GET `/audit/query?fromTs={t1}&toTs={t2}` where t1 < t2. | Returns logs with timestamp in range [t1, t2]. | |
| 3.5 | Combine filters: `userId=42&action=LOGIN&status=SUCCESS`. | Returns only logs matching all filters. | |
| 3.6 | GET `/audit/query?fromTs={t2}&toTs={t1}` where t1 < t2. | Returns 400 Bad Request, code `AUDIT_INVALID_DATE_RANGE`. | |

## 4. Query Audit Logs — Pagination
**Goal**: Verify pagination parameters work correctly.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | GET `/audit/query?page=0&size=5`. | Returns first 5 logs. `totalElements` reflects total count. | |
| 4.2 | GET `/audit/query?page=1&size=5`. | Returns next 5 logs (if any exist). | |
| 4.3 | Request page beyond `totalPages`. | Returns 200 OK with empty `content` list. | |

## 5. End-to-End Security Audit Scenario
**Goal**: Verify the complete audit trail across multiple services.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | User registers a new account. | `auth-service` publishes `AuditLogEvent` with action=`REGISTER`. | |
| 5.2 | GET `/audit/query?userId={newUserId}`. | One REGISTER record visible. | |
| 5.3 | User logs in successfully. | New LOGIN/SUCCESS record created. | |
| 5.4 | User subscribes to a campaign package. | New SUBSCRIBE record created. | |
| 5.5 | User makes a payment. | New PAYMENT record created. | |
| 5.6 | GET `/audit/query?userId={userId}`. | All 4 records visible: REGISTER, LOGIN, SUBSCRIBE, PAYMENT, sorted newest first. | |
| 5.7 | Filter by `action=PAYMENT`. | Only the PAYMENT row is returned. | |
| 5.8 | Filter by `status=SUCCESS`. | All successful actions returned. | |

## 6. Metadata JSON Verification
**Goal**: Verify metadata field captures additional context.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 6.1 | Trigger a login with specific User-Agent header. | Audit log `metadataJson` contains `{"userAgent":"..."}`. | |
| 6.2 | Trigger a payment with package details. | Audit log `metadataJson` contains package code and amount. | |
| 6.3 | Parse `metadataJson` as JSON. | Valid JSON structure, no parse errors. | |
