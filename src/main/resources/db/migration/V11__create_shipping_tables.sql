CREATE TABLE deliveries (
    id                          BIGSERIAL PRIMARY KEY,
    order_id                    BIGINT NOT NULL UNIQUE,
    shipper_id                  BIGINT REFERENCES users(id),
    status                      VARCHAR(20) NOT NULL,
    failure_reason              VARCHAR(30),
    retry_count                 INTEGER NOT NULL DEFAULT 0,
    assigned_at                 TIMESTAMP NOT NULL DEFAULT now(),
    picked_up_at                TIMESTAMP,
    delivered_at                TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_deliveries_shipper_id ON deliveries(shipper_id);

CREATE TABLE delivery_status_history (
    id                          BIGSERIAL PRIMARY KEY,
    delivery_id                 BIGINT NOT NULL REFERENCES deliveries(id),
    from_status                 VARCHAR(20),
    to_status                   VARCHAR(20) NOT NULL,
    changed_by                  BIGINT REFERENCES users(id),
    reason                      VARCHAR(500),
    created_at                  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_status_history_delivery_id ON delivery_status_history(delivery_id);
