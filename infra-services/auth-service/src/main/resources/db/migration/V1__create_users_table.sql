CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    msisdn VARCHAR(20) UNIQUE,
    email VARCHAR(120) UNIQUE,
    password_hash VARCHAR(72),
    credit_balance INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_users_msisdn ON users(msisdn);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);
