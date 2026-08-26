-- idx_products_name_trgm (V13) duoc tao tren cot name, nhung ProductSpecifications.nameContains()
-- query bang lower(name) LIKE ? -> predicate va index khong khop bieu thuc, Postgres khong dung
-- duoc index nay (xac nhan qua EXPLAIN ANALYZE: van Seq Scan du index ton tai). Sua lai index tren
-- dung bieu thuc lower(name) de khop voi query that cua app.
DROP INDEX idx_products_name_trgm;
CREATE INDEX idx_products_name_trgm ON products USING GIN (lower(name) gin_trgm_ops);
