CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    msisdn VARCHAR(20) NOT NULL,
    package_code VARCHAR(50) NOT NULL,
    amount_mmk BIGINT NOT NULL,
    credit_amount INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    mps_ref VARCHAR(100),
    error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_payment_user ON payment_transactions(user_id);
CREATE INDEX idx_payment_status ON payment_transactions(status);
CREATE INDEX idx_payment_created ON payment_transactions(created_at);
