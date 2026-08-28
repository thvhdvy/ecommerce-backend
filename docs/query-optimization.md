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

## Tóm tắt (v1)

| Index | Kết quả |
|---|---|
| `idx_products_category_id_price` | Đúng như kỳ vọng — ~150x |
| `idx_products_name_trgm` | **Bug**: sai biểu thức, 0% được dùng → đã fix ở V18, sau fix ~100x |
| `idx_orders_customer_id_created_at` | Đúng hướng nhưng chưa phát huy tác dụng — cần thêm pagination mới thấy ~20x |
| `idx_order_status_history_to_status_created_at` | Đúng như kỳ vọng cho truy vấn hẹp ngày — ~13x |
| `idx_refunds_order_id_status` | Chưa đo được khác biệt ở quy mô hiện tại; shape index chưa khớp lý tưởng với query thật |

## v2 module — index cho 4 module mới (Coupon, Return, Notification, Shipment-split)

Cùng phương pháp với v1, DB scratch riêng (không phải Testcontainers), migration `V1`..`V24`. Khác biệt
về seed: các bảng v2 tham chiếu `Order`/`OrderItem`/`Seller` bằng ID thường (không FK, xem design doc
mục 0.8) nên **không cần seed lại orders/products** — chỉ cần `users` thật cho các cột có FK
(`user_id`, `coupon_id`). Quy mô seed: 20,000 `users`, 500 `coupons`, 200,000 `coupon_redemptions`,
500,000 `notifications` (~200 dòng `PENDING` due), 100,000 `return_requests` (~200 dòng `APPROVED`
quá hạn), 300,000 `deliveries`.

### 1. Notification dispatcher poll (`idx_notifications_status_next_retry_at`, V22)

Query thật của `NotificationDispatcher` (chạy định kỳ, mọi request scheduler):
`WHERE status = 'PENDING' AND next_retry_at <= now() ORDER BY id LIMIT 100`.

| | Plan | Execution Time |
|---|---|---|
| **Trước** (không có index) | `Parallel Index Scan` trên PK, filter toàn bộ 500K dòng | 78.96 ms |
| **Sau** (composite `(status, next_retry_at)`) | `Index Scan` trực tiếp, chỉ đọc đúng ~200 dòng khớp | 0.16 ms |

**~500x**. Đây là bảng phát triển không giới hạn theo thời gian (mỗi order transition tạo 1 dòng) —
index này càng quan trọng khi bảng lớn dần, vì tập `PENDING` cần quét luôn nhỏ và cố định trong khi
toàn bảng tăng vô hạn.

### 2. Return auto-expire poll (`idx_return_requests_status_expires_at`, V21) — phát hiện: index thừa về mặt đo lường

Query thật của `ReturnMaintenanceScheduler`: `WHERE status = 'APPROVED' AND expires_at < now() ORDER BY id LIMIT 100`.

| | Plan | Execution Time |
|---|---|---|
| **Trước** (đã `DROP` `idx_return_requests_status_expires_at`) | `Index Scan` trên `uq_return_requests_active` (partial unique index của mục 6.4/7.6, filter thêm `expires_at`) | 0.077 ms |
| **Sau** (có `idx_return_requests_status_expires_at`) | `Index Scan` trên chính index này | 0.084 ms |

**Không khác biệt đo được** — và lý do khác hẳn case `refunds` ở v1 (không phải "bảng còn nhỏ"). Chính
`uq_return_requests_active` (partial unique index chặn race condition, mục 6.4) tình cờ **đã là 1
covering index rất hẹp** cho đúng truy vấn này: nó chỉ chứa các dòng `status IN (REQUESTED, APPROVED,
ITEM_RECEIVED, REFUND_PENDING, REFUND_FAILED)` — tức đúng tập "chưa terminal", theo thiết kế luôn nhỏ
và bị chặn không phình to (mỗi return request giải quyết xong sẽ rời khỏi tập này). Planner tận dụng
được index đó thay vì cần quét toàn bảng dù nó không được tạo ra cho mục đích này.

### 3. Coupon redemption lookup theo order (`idx_coupon_redemptions_order_id`, V20)

Query thật của `CouponServiceImpl.commit()`/`release()` (chạy mỗi lần payment confirm/cancel order có
dùng coupon): `WHERE order_id = ?`.

| | Plan | Execution Time |
|---|---|---|
| **Trước** (không có index) | `Seq Scan`, quét 200K dòng | 19.82 ms |
| **Sau** | `Index Scan` | 0.038 ms |

**~520x**. Đáng chú ý vì đây nằm trên hot path checkout/cancel, không phải job nền — độ trễ này cộng
trực tiếp vào response time của khách khi order có coupon.

### 4. Deliveries lookup theo order (`idx_deliveries_order_id`, V24) — phát hiện: index dư thừa, nên bỏ

Query thật của `ShippingServiceImpl.getDeliveryStatusesBySeller()`, gọi bởi
`OrderServiceImpl.recomputeAggregateStatus()` — **chạy mỗi lần bất kỳ delivery nào đổi trạng thái**
(assign, in-transit, delivered, failed) trong toàn bộ order, không riêng gì delivery vừa đổi:
`WHERE order_id = ?`.

| | Plan | Execution Time |
|---|---|---|
| **Trước** (đã `DROP` `idx_deliveries_order_id`) | `Index Scan` trên `uq_deliveries_order_seller` (constraint `UNIQUE(order_id, seller_id)`, V24) | 0.032 ms |
| **Sau** (có `idx_deliveries_order_id`) | `Index Scan` trên chính index mới | 0.040 ms |

**Không khác biệt — và lần này là index thật sự thừa**, không phải "chưa phát huy tác dụng ở quy mô
hiện tại" như case `refunds`/Return ở trên. `order_id` là cột dẫn đầu (leading column) của unique
constraint `(order_id, seller_id)` đã có sẵn từ chính migration `V24` — Postgres dùng được index đó cho
điều kiện chỉ lọc theo `order_id` (prefix match trên composite index), nên `idx_deliveries_order_id`
không bao giờ được planner chọn ưu tiên hơn, chỉ tốn thêm dung lượng + chi phí ghi mỗi lần
insert/update `deliveries` mà không mang lại lợi ích đọc nào. Đây là bug tương tự tinh thần case
`idx_products_name_trgm` ở v1 (mục 2: "index tưởng có tác dụng nhưng đo thật mới lộ ra") — khác ở chỗ
lần này không sai biểu thức, mà thừa hoàn toàn vì trùng với 1 index khác đã tồn tại.

**Đã fix**: [`V25__drop_redundant_deliveries_order_id_index.sql`](../src/main/resources/db/migration/V25__drop_redundant_deliveries_order_id_index.sql) — `DROP INDEX`, không sửa lại `V24` đã chạy production (đúng nguyên tắc Flyway: migration đã áp dụng không được sửa, chỉ thêm migration mới).

## Tóm tắt (v2)

| Index | Kết quả |
|---|---|
| `idx_notifications_status_next_retry_at` | Đúng như kỳ vọng — ~500x, quan trọng dần khi bảng lớn |
| `idx_return_requests_status_expires_at` | Không đo được khác biệt — `uq_return_requests_active` (index khác, tạo cho mục đích concurrency) tình cờ đã covering đúng truy vấn này |
| `idx_coupon_redemptions_order_id` | Đúng như kỳ vọng — ~520x, nằm trên hot path checkout/cancel |
| `idx_deliveries_order_id` | **Thừa** — trùng leading column với `uq_deliveries_order_seller` đã có sẵn → đã `DROP` ở `V25` |
