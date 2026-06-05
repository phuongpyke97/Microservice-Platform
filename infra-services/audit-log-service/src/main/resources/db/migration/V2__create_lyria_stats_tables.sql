-- Bảng lưu chi tiết từng lượt gọi API tạo nhạc AI (Dành cho tab Chi tiết request)
CREATE TABLE lyria_request_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    msisdn VARCHAR(50),
    model VARCHAR(100) NOT NULL,
    prompt_tokens INT NOT NULL,
    candidate_tokens INT NOT NULL,
    total_tokens INT NOT NULL,
    latency_ms INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lyria_request_logs_msisdn ON lyria_request_logs(msisdn);
CREATE INDEX idx_lyria_request_logs_created_at ON lyria_request_logs(created_at);

-- Bảng lưu tổng hợp theo ngày (Dành cho tab Biểu đồ và Bảng dữ liệu)
CREATE TABLE lyria_daily_stats (
    stat_date DATE PRIMARY KEY,
    total_requests INT NOT NULL DEFAULT 0,
    failed_requests INT NOT NULL DEFAULT 0,
    total_prompt_tokens BIGINT NOT NULL DEFAULT 0,
    total_candidate_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    avg_latency_ms INT NOT NULL DEFAULT 0,
    estimated_cost_usd DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);
