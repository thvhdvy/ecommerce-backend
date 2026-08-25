-- Composite index: ho tro filter theo category + sort/range theo gia cung luc (GET /api/products)
CREATE INDEX idx_products_category_id_price ON products(category_id, price);

-- pg_trgm: ho tro tim kiem ten san pham dang LIKE/ILIKE '%keyword%' (khong the dung B-tree cho contains-search)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);

-- Thay idx_orders_customer_id (don cot) bang composite (customer_id, created_at DESC)
-- de vua loc theo customer vua tra ve theo thu tu moi nhat ma khong can sort rieng.
DROP INDEX idx_orders_customer_id;
CREATE INDEX idx_orders_customer_id_created_at ON orders(customer_id, created_at DESC);

-- Seller tra cuu order_items cua minh (SellerOrderController / OrderItemRepository.findAllBySellerId...)
CREATE INDEX idx_order_items_seller_id ON order_items(seller_id);
