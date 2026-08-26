# Query Optimization — EXPLAIN ANALYZE Evidence (Phase 5)

Phase 5 (design doc, mục Roadmap) yêu cầu index tuning "đo bằng `EXPLAIN ANALYZE`". Tài liệu này ghi lại
cách đo và kết quả thật — không chỉ liệt kê index đã thêm.

## Phương pháp

- Postgres 18 chạy riêng (container tạm, không phải Testcontainers của test suite), áp toàn bộ migration
  `V1`..`V18` bằng `psql`.
- Seed dữ liệu tổng hợp ở quy mô đủ lớn để index có tác dụng đo được: 200,000 `products`, 300,000 `orders`,
  ~210,000 `order_status_history`/`payments`, ~10,000 `refunds`, 600,000 `order_items`.
- Với mỗi query, chạy `EXPLAIN (ANALYZE, BUFFERS)` ở 2 trạng thái: **trước** (drop index liên quan, giữ
  index cũ nếu có) và **sau** (tạo lại index như migration hiện tại), cùng dữ liệu, để so sánh trực tiếp.

## 1. Product search — filter category + khoảng giá (`idx_products_category_id_price`, V13)

Query thực tế sinh ra từ `ProductSpecifications` (category + price range + sort theo giá, `LIMIT 20`).

| | Plan | Execution Time |
|---|---|---|
| **Trước** (chỉ có `idx_products_category_id` đơn cột) | `Bitmap Heap Scan` + `Sort` (top-N heapsort) | 24.66 ms |
| **Sau** (composite `(category_id, price)`) | `Index Scan` trực tiếp, không cần sort riêng | 0.16 ms |

**~150x**. Composite index cho phép Postgres vừa lọc `category_id` vừa dùng thứ tự `price` có sẵn trong
index để trả kết quả đã sort, không cần bitmap scan + heapsort riêng.

## 2. Product name search (`idx_products_name_trgm`, V13) — phát hiện bug khi đo thật

Query thực tế: `ProductSpecifications.nameContains()` dùng `cb.lower(name) LIKE pattern`, Hibernate sinh
SQL `lower(name) LIKE '%keyword%'`.

**Đo lần đầu — index tồn tại nhưng KHÔNG được dùng:**

| | Plan | Execution Time |
|---|---|---|
| Không có index | `Parallel Seq Scan`, filter `lower(name) LIKE ...` | 39.17 ms |
| Có `idx_products_name_trgm` (GIN trên cột `name` gốc) | **Vẫn `Parallel Seq Scan`** — index bị bỏ qua hoàn toàn | 39.22 ms |

Nguyên nhân: index được tạo trên biểu thức `name`, nhưng predicate thật của app là trên biểu thức
`lower(name)` — hai biểu thức khác nhau, Postgres không match được index với query. Đây là index "chết"
từ ngày tạo (V13), không phải do dữ liệu ít — kiểm chứng bằng cách thử `name ILIKE '%keyword%'` (không bọc
`lower()`) thì index chạy đúng ngay (0.49 ms).

**Fix:** [`V18__fix_product_name_trgm_index_expression.sql`](../src/main/resources/db/migration/V18__fix_product_name_trgm_index_expression.sql)
— đổi index sang đúng biểu thức `lower(name)`.

| | Plan | Execution Time |
|---|---|---|
| Trước fix (index sai biểu thức) | Seq Scan | 39.17 ms |
| Sau fix (index đúng biểu thức `lower(name)`) | `Bitmap Index Scan` trên `idx_products_name_trgm` | 0.38 ms |

**~100x**, và quan trọng hơn: đây là lần đầu index này thực sự hoạt động kể từ khi được thêm.

## 3. Order list theo customer, sort theo `created_at DESC` (`idx_orders_customer_id_created_at`, V13)

Query thực tế: `findAllByCustomerIdOrderByCreatedAtDesc` — **không có `LIMIT`/pagination**.

| Kịch bản | Trước (đơn cột `customer_id`) | Sau (composite `customer_id, created_at DESC`) |
|---|---|---|
| Khách ~90 đơn (trung bình trong bộ seed) | 0.41 ms | 0.85 ms (không khác biệt đáng kể, nhiễu đo) |
| Khách 5,000 đơn, **không** LIMIT | 3.45 ms | 3.69 ms (không khác biệt) |
| Khách 5,000 đơn, **có** `LIMIT 20` (mô phỏng pagination) | 2.23 ms (`Sort` + `Bitmap Heap Scan`) | **0.11 ms** (`Index Scan` trực tiếp, dừng sớm nhờ index đã có thứ tự) |

**Phát hiện trung thực:** composite index này hiện **không mang lại lợi ích đo được** cho endpoint thật
(`GET /api/orders` trả `List` không giới hạn) — Postgres bitmap-scan + quicksort đã đủ nhanh khi phải đọc
toàn bộ kết quả. Lợi ích ~20x của composite index chỉ xuất hiện khi query có `LIMIT` (tức khi
endpoint được thêm pagination) — điều mà `OrderService.listMyOrders()`/`listAllOrders()` hiện chưa làm.
Ghi nhận: index đã đúng hướng, sẵn sàng cho lúc thêm pagination, nhưng chưa phát huy tác dụng ở dạng
endpoint hiện tại.

## 4. Report `revenueByDay` — `order_status_history(to_status, created_at)` (V16)

| Khoảng ngày | Trước (không có index) | Sau (có index) |
|---|---|---|
| 1 ngày gần nhất (~421 dòng khớp / 210K) | 14.65 ms (`Parallel Seq Scan`) | **1.09 ms** (`Index Scan`) — **~13x** |
| 30 ngày gần nhất (~8,908 dòng khớp / 210K, ~4.2%) | 81.49 ms | 75.24 ms — không khác biệt |

Ở độ chọn lọc thấp (30 ngày ≈ 4% bảng, bảng đủ nhỏ để nằm gọn trong cache), planner **chủ động chọn seq
scan** vì rẻ hơn — đây là quyết định đúng của Postgres, không phải index vô dụng. Index phát huy tác dụng
rõ ở truy vấn hẹp ngày (drill-down 1 ngày cụ thể), và sẽ càng quan trọng khi `order_status_history` lớn
dần theo thời gian (mỗi order tạo ra vài dòng lịch sử suốt vòng đời).

## 5. Report refunds lookup — `refunds(order_id, status)` (V16)

| | Execution Time |
|---|---|
| Trước (không có index) | 10.38 ms |
| Sau (có `idx_refunds_order_id_status`) | 8.34 ms — không khác biệt đáng kể |

**Quan sát:** ở quy mô hiện tại (~10K dòng `refunds`), bảng đủ nhỏ để seq scan luôn thắng. Nhưng đáng chú
ý hơn: query thật trong `revenueByDay` là `GROUP BY order_id` trên **toàn bộ** dòng `status = 'REFUNDED'`
(không lọc theo 1 `order_id` cụ thể) — composite `(order_id, status)` hợp với truy vấn dạng "tra 1 order
cụ thể", không phải dạng full-aggregate này. Một index đơn cột `status` (hoặc partial index
`WHERE status = 'REFUNDED'`) sẽ khớp đúng shape truy vấn hơn. Chưa sửa vì (a) không sai, chỉ chưa tối ưu
hình dạng, (b) ở quy mô dữ liệu thật của project này khác biệt không đáng kể — ghi nhận làm điểm cải tiến
tiếp theo nếu bảng `refunds` lớn dần.

## Tóm tắt

| Index | Kết quả |
|---|---|
| `idx_products_category_id_price` | Đúng như kỳ vọng — ~150x |
| `idx_products_name_trgm` | **Bug**: sai biểu thức, 0% được dùng → đã fix ở V18, sau fix ~100x |
| `idx_orders_customer_id_created_at` | Đúng hướng nhưng chưa phát huy tác dụng — cần thêm pagination mới thấy ~20x |
| `idx_order_status_history_to_status_created_at` | Đúng như kỳ vọng cho truy vấn hẹp ngày — ~13x |
| `idx_refunds_order_id_status` | Chưa đo được khác biệt ở quy mô hiện tại; shape index chưa khớp lý tưởng với query thật |
