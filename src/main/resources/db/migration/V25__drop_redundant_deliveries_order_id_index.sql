-- idx_deliveries_order_id (V24) la index thua: order_id la leading column cua unique constraint
-- uq_deliveries_order_seller (order_id, seller_id) da co san tu chinh V24, nen Postgres luon dung
-- duoc constraint index do cho dieu kien WHERE order_id = ? (prefix match tren composite index).
-- Phat hien qua benchmark EXPLAIN ANALYZE thuc te — xem docs/query-optimization.md muc "v2 module #4".
DROP INDEX idx_deliveries_order_id;
