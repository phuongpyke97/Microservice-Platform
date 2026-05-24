CREATE TABLE ringtone_assignments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    msisdn VARCHAR(20) NOT NULL,
    ringtone_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    mytone_transaction_id VARCHAR(100),
    error_message VARCHAR(500),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_ringtone_assignments_user_id ON ringtone_assignments(user_id);
CREATE INDEX idx_ringtone_assignments_msisdn ON ringtone_assignments(msisdn);
CREATE INDEX idx_ringtone_assignments_status ON ringtone_assignments(status);
