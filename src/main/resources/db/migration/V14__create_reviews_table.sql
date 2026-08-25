CREATE TABLE reviews (
    id                          BIGSERIAL PRIMARY KEY,
    product_id                  BIGINT NOT NULL REFERENCES products(id),
    user_id                     BIGINT NOT NULL REFERENCES users(id),
    order_id                    BIGINT NOT NULL,
    rating                      INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment                     VARCHAR(2000),
    status                      VARCHAR(20) NOT NULL,
    created_at                  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_product_user UNIQUE (product_id, user_id)
);

CREATE INDEX idx_reviews_product_id ON reviews(product_id);
