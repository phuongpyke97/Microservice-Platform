CREATE TABLE campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE TABLE campaign_packages (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaigns(id),
    name VARCHAR(100) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    credit_amount INT NOT NULL,
    validity_days INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL REFERENCES campaign_packages(id),
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_campaigns_status_dates ON campaigns(status, start_at, end_at);
CREATE INDEX idx_campaign_packages_campaign_id ON campaign_packages(campaign_id);
CREATE INDEX idx_user_subscriptions_user_status ON user_subscriptions(user_id, status);
