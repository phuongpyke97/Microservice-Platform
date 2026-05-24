# CRBT Campaign Service UAT Test Cases

## 1. Create Campaign
**Goal**: Verify that campaigns and associated packages can be created successfully.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 1.1 | POST `/campaigns` with valid campaign and package data. | Returns 200 OK, campaign and packages are saved to DB. | |
| 1.2 | Check DB `campaigns` and `campaign_packages`. | Records exist with correct status `ACTIVE` and cascaded packages. | |

## 2. Get Active Campaigns
**Goal**: Verify that users can fetch currently active campaigns.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 2.1 | GET `/campaigns/active` while there is an active campaign. | Returns 200 OK with list of active campaigns and packages. | |
| 2.2 | Create a campaign with `endAt` in the past, then GET `/campaigns/active`. | Returns 200 OK, expired campaign is excluded from the list. | |

## 3. Subscribe to Package
**Goal**: Verify package subscription, credit calculation, and wallet integration.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 3.1 | POST `/campaigns/subscribe` with a valid package ID. | Returns 200 OK. | |
| 3.2 | Check DB `user_subscriptions`. | Subscription record created with `status=ACTIVE`, `expiresAt` calculated correctly. | |
| 3.3 | Check RabbitMQ `credit-events-exchange`. | `CreditChangedEvent` published with correct base amount (if price < 1000). | |
| 3.4 | Subscribe to a package with price >= 1000. | `CreditChangedEvent` published with +10% bonus amount. | |
| 3.5 | Try subscribing to a package from an expired campaign. | Returns 404 Not Found, code `CAMPAIGN_NOT_FOUND`. | |

## 4. Auto-Renewal Job
**Goal**: Verify daily auto-renewal process for active subscriptions.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 4.1 | Manually update a test subscription in DB: `expiresAt = yesterday`, `autoRenew = true`. | DB record updated successfully. | |
| 4.2 | Trigger `CampaignScheduler.autoRenewSubscriptions()` manually or wait. | Job runs without errors. | |
| 4.3 | Check DB `user_subscriptions`. | `expiresAt` is extended by `validityDays`. | |
| 4.4 | Check RabbitMQ `credit-events-exchange`. | `CreditChangedEvent` published for the renewal with correct amount/bonus. | |

## 5. Generate AI Music
**Goal**: Verify integration with Google Lyria 3.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 5.1 | POST `/campaigns/generate?genre=Pop&mood=Happy&instrument=Piano`. | Returns 200 OK with binary audio data payload. | |
| 5.2 | Try generating with missing parameters. | Returns 400 Bad Request. | |

## 6. End-to-End Scenario
**Goal**: Verify campaign creation to user subscription to wallet update.

| Step | Action | Expected Result | Status |
|---|---|---|---|
| 6.1 | Admin creates a "Summer Hit" campaign with a 1500-price package (100 credits). | Campaign and package created. | |
| 6.2 | User fetches active campaigns, selects the "Summer Hit" package ID. | Package ID obtained. | |
| 6.3 | User subscribes to the package. | Subscription created, event published. | |
| 6.4 | Check user's wallet via `credit-wallet-service`. | User's wallet balance increased by 110 credits (100 base + 10% bonus). | |