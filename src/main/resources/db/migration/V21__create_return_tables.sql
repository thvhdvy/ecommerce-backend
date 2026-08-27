CREATE TABLE return_requests (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users (id),
    seller_id BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL,
    note VARCHAR(1000),
    refund_amount_snapshot NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    approved_at TIMESTAMP,
    item_received_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE return_status_history (
    id BIGSERIAL PRIMARY KEY,
    return_request_id BIGINT NOT NULL REFERENCES return_requests (id),
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by BIGINT REFERENCES users (id),
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);

-- Chan tao request moi cho cung 1 order_item khi da co 1 request khac CHUA terminal (design doc
-- muc 7.6 — REFUND_FAILED van tinh la "dang xu ly", cho admin retry, khong duoc coi la terminal).
-- Chi REJECTED/CANCELLED/EXPIRED/REFUNDED moi cho phep tao request moi.
CREATE UNIQUE INDEX uq_return_requests_active
    ON return_requests (order_item_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'ITEM_RECEIVED', 'REFUND_PENDING', 'REFUND_FAILED');

CREATE INDEX idx_return_requests_order_id ON return_requests (order_id);
CREATE INDEX idx_return_requests_user_id ON return_requests (user_id);
CREATE INDEX idx_return_requests_seller_id ON return_requests (seller_id);
-- Phuc vu scheduled job auto-expire (WHERE status = 'APPROVED' AND expires_at < now()).
CREATE INDEX idx_return_requests_status_expires_at ON return_requests (status, expires_at);

CREATE INDEX idx_return_status_history_return_request_id ON return_status_history (return_request_id);

-- Phan biet refund do return (module nay) voi refund do cancel order thong thuong (design doc muc 7.3).
ALTER TABLE refunds ADD COLUMN return_request_id BIGINT;
