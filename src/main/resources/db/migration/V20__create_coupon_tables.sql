CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(19, 2) NOT NULL,
    max_discount_amount NUMERIC(19, 2),
    min_order_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    usage_limit INTEGER,
    usage_reserved INTEGER NOT NULL DEFAULT 0,
    usage_committed INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    starts_at TIMESTAMP,
    ends_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE coupon_redemptions (
    id BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT NOT NULL REFERENCES coupons (id),
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users (id),
    discount_amount_snapshot NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Chan 1 user dung trung 1 coupon khi con dang RESERVED/COMMITTED (design doc muc 6.4) —
-- partial unique index thay vi SELECT COUNT roi INSERT de tranh race giua 2 request dong thoi.
CREATE UNIQUE INDEX uq_coupon_redemptions_active
    ON coupon_redemptions (coupon_id, user_id)
    WHERE status IN ('RESERVED', 'COMMITTED');

CREATE INDEX idx_coupon_redemptions_order_id ON coupon_redemptions (order_id);
CREATE INDEX idx_coupon_redemptions_coupon_id ON coupon_redemptions (coupon_id);

ALTER TABLE orders ADD COLUMN coupon_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(19, 2) NOT NULL DEFAULT 0;
