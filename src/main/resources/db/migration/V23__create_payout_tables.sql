CREATE TABLE seller_ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL,
    order_id BIGINT,
    return_request_id BIGINT,
    type VARCHAR(20) NOT NULL,
    gross_amount NUMERIC(19, 2),
    commission_amount NUMERIC(19, 2),
    net_amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Idempotency guard: neu event orders.status -> COMPLETED vo tinh fire 2 lan (vd ca job va 1 entry
-- point khac cung khop dieu kien) thi lan 2 se vi pham unique nay thay vi cong tien 2 lan
-- (design doc v2 muc 9.3 — cung tinh than voi payment_webhook_events).
CREATE UNIQUE INDEX uq_seller_ledger_entries_earned ON seller_ledger_entries (order_id, seller_id)
    WHERE type = 'EARNED';

CREATE INDEX idx_seller_ledger_entries_seller_id ON seller_ledger_entries (seller_id, created_at);

CREATE TABLE seller_balances (
    seller_id BIGINT PRIMARY KEY,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE seller_payouts (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_seller_payouts_seller_id ON seller_payouts (seller_id, created_at);
