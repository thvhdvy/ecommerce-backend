CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    vnp_txn_ref VARCHAR(255) NOT NULL UNIQUE,
    vnp_transaction_no VARCHAR(255),
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments (id),
    order_id BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    vnp_refund_transaction_no VARCHAR(255),
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);

CREATE TABLE payment_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    vnp_transaction_no VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    processed_at TIMESTAMP NOT NULL
);
