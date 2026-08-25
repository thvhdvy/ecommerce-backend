CREATE TABLE orders (
    id                          BIGSERIAL PRIMARY KEY,
    customer_id                 BIGINT NOT NULL REFERENCES users(id),
    status                      VARCHAR(30) NOT NULL,
    total_amount                NUMERIC(12, 2) NOT NULL,
    shipping_recipient_name     VARCHAR(255) NOT NULL,
    shipping_phone              VARCHAR(50) NOT NULL,
    shipping_address            VARCHAR(500) NOT NULL,
    shipping_note               VARCHAR(500),
    created_at                  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
    id                          BIGSERIAL PRIMARY KEY,
    order_id                    BIGINT NOT NULL REFERENCES orders(id),
    product_id                  BIGINT NOT NULL,
    seller_id                   BIGINT NOT NULL,
    product_name_snapshot       VARCHAR(255) NOT NULL,
    unit_price_snapshot         NUMERIC(12, 2) NOT NULL,
    quantity                    INTEGER NOT NULL,
    item_status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

CREATE TABLE order_status_history (
    id                          BIGSERIAL PRIMARY KEY,
    order_id                    BIGINT NOT NULL REFERENCES orders(id),
    from_status                 VARCHAR(30),
    to_status                   VARCHAR(30) NOT NULL,
    changed_by                  BIGINT REFERENCES users(id),
    reason                      VARCHAR(500),
    created_at                  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);
