-- Phase 12 (design doc v2 muc 10): tach shipment theo tung seller trong 1 order thay vi 1
-- delivery/order. deliveries tro thanh 1:N voi orders (moi seller trong order co 1 delivery rieng).
ALTER TABLE deliveries DROP CONSTRAINT deliveries_order_id_key;

ALTER TABLE deliveries ADD COLUMN seller_id BIGINT NOT NULL;

ALTER TABLE deliveries ADD CONSTRAINT uq_deliveries_order_seller UNIQUE (order_id, seller_id);

CREATE INDEX idx_deliveries_order_id ON deliveries(order_id);
