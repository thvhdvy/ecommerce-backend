CREATE TABLE inventory (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT NOT NULL UNIQUE REFERENCES products(id),
    quantity_available  INTEGER NOT NULL DEFAULT 0,
    quantity_reserved   INTEGER NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);
