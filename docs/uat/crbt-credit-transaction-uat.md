# CRBT Credit Transaction Service UAT Test Cases

## 1. Event-Driven Record Creation
**Goal**: Verify transactions are recorded automatically from credit change events.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | Trigger a credit deduction in `credit-wallet-service` (e.g., subscribe to a campaign package). | `credit-wallet-service` publishes `CreditChangedEvent` to RabbitMQ. | |
| 1.2 | Check `crbt-credit-transaction-service` DB. | New record created in `credit_transactions` with correct userId, amount, direction=`DEBIT`, reason, referenceId, and timestamp. | |
| 1.3 | Trigger a credit top-up (e.g., wallet add). | New record created with direction=`CREDIT`. | |
| 1.4 | Attempt to UPDATE or DELETE a record directly. | No such API exposed; operation not possible via service. | |

## 2. History Query — Basic
**Goal**: Verify users can retrieve their transaction history.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | GET `/credit-transactions/history`. | Returns 200 OK, paginated list of all transactions for the current user, sorted by timestamp desc. | |
| 2.2 | Verify another user's transactions are not included. | Response only contains records where `userId` matches the authenticated user. | |
| 2.3 | GET `/credit-transactions/history` on account with no transactions. | Returns 200 OK, empty `content` list, `totalElements: 0`. | |

## 3. History Query — Filters
**Goal**: Verify filter parameters work correctly.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | GET `/credit-transactions/history?direction=DEBIT`. | Returns only DEBIT transactions. | |
| 3.2 | GET `/credit-transactions/history?direction=CREDIT`. | Returns only CREDIT transactions. | |
| 3.3 | GET `/credit-transactions/history?reason=subscribe`. | Returns transactions where reason contains "subscribe" (case-insensitive). | |
| 3.4 | GET `/credit-transactions/history?fromTs={t1}&toTs={t2}` where t1 < t2. | Returns transactions with timestamp in range [t1, t2]. | |
| 3.5 | Combine filters: `direction=DEBIT&reason=package`. | Returns only DEBIT transactions matching reason. | |
| 3.6 | GET `/credit-transactions/history?fromTs={t2}&toTs={t1}` where t1 < t2. | Returns 400 Bad Request, code `CREDIT_TRANSACTION_INVALID_DATE_RANGE`. | |

## 4. History Query — Pagination
**Goal**: Verify pagination parameters work correctly.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | GET `/credit-transactions/history?page=0&size=5`. | Returns first 5 transactions. `totalElements` reflects total count. | |
| 4.2 | GET `/credit-transactions/history?page=1&size=5`. | Returns next 5 transactions (if any exist). | |
| 4.3 | Request page beyond `totalPages`. | Returns 200 OK with empty `content` list. | |

## 5. CSV Export
**Goal**: Verify the CSV export endpoint returns correct data.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | GET `/credit-transactions/export`. | Returns 200 OK, Content-Type: `text/csv`, header row + data rows for all user transactions. | |
| 5.2 | Verify CSV structure. | Has columns: ID, User ID, Amount, Direction, Reason, Reference ID, Timestamp, Created At. | |
| 5.3 | GET `/credit-transactions/export?direction=DEBIT`. | CSV contains only DEBIT rows. | |
| 5.4 | GET `/credit-transactions/export?fromTs={t2}&toTs={t1}` (invalid range). | Returns 400 Bad Request. | |
| 5.5 | GET `/credit-transactions/export` with no transactions. | Returns 200 OK with only the header row. | |

## 6. End-to-End Financial Reconciliation Scenario
**Goal**: Verify the complete financial audit trail across multiple services.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 6.1 | Check initial credit balance: 1000 credits. | Wallet balance confirmed. | |
| 6.2 | Subscribe to a campaign package costing 500 credits. | Wallet deducted 500, `CreditChangedEvent` published. | |
| 6.3 | GET `/credit-transactions/history`. | One DEBIT record visible with amount=500, reason referencing the subscription. | |
| 6.4 | Admin adds 200 credits to wallet. | `CreditChangedEvent` published. | |
| 6.5 | GET `/credit-transactions/history`. | Two records visible: DEBIT 500, CREDIT 200, sorted newest first. | |
| 6.6 | Export to CSV. | Both rows appear with correct fields. | |
| 6.7 | Filter by `direction=DEBIT`. | Only the 500 DEBIT row is returned. | |
