# E-commerce Backend API

[![CI](https://github.com/thvhdvy/ecommerce-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/thvhdvy/ecommerce-backend/actions/workflows/ci.yml)

Backend e-commerce viết bằng Java/Spring Boot. Schema, state machine, API contract, business rule — chi tiết đầy đủ nằm ở [`ecommerce-backend-design.md`](ecommerce-backend-design.md), README chỉ tóm tắt điều hướng.

Bằng chứng đo `EXPLAIN ANALYZE` cho các index đã thêm (Phase 5): [`docs/query-optimization.md`](docs/query-optimization.md).

## Kiến trúc

**Modular Monolith** — package theo feature, không theo layer ở top level:

`user` · `product` · `inventory` · `cart` · `order` · `payment` · `shipping` · `report`

Quy ước ranh giới module (chi tiết ở design doc mục 0.8):
- Module giao dịch cốt lõi (Order, Payment, Inventory, Shipping) tham chiếu chéo bằng ID thường, **không FK**.
- Module dữ liệu nền (User, Seller, Product, Category) dùng FK thật.
- Module chỉ gọi module khác qua service interface — không query thẳng repository/entity của module khác (ngoại lệ duy nhất: module `report`, chỉ đọc, cần JOIN nhiều bảng để aggregate).

## Tech stack

| | |
|---|---|
| Ngôn ngữ / Framework | Java 21, Spring Boot 4 |
| DB / Migration | PostgreSQL, Flyway |
| Auth | Spring Security + JWT (access + refresh token) |
| Payment | VNPay sandbox (redirect payment + IPN + refund API, tự build HMAC-SHA512) |
| API docs | springdoc-openapi (Swagger UI) |
| Test | JUnit 5, Mockito, Testcontainers (PostgreSQL thật cho integration test) |
| Observability | Spring Boot Actuator + Micrometer/Prometheus, structured JSON logging |
| Container | Docker, Docker Compose |

## Cách chạy

### 1. Local (Maven)

Yêu cầu PostgreSQL chạy sẵn (local hoặc container riêng).

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
# điền DB credentials, jwt.secret, VNPay sandbox credentials vào file vừa tạo
./mvnw spring-boot:run
```

### 2. Docker Compose (khuyến nghị — không cần cài Postgres)

```bash
cp .env.example .env
# điền secret vào .env
docker compose up --build
```

App chạy ở `http://localhost:8080`, Postgres expose ở `5432`. Migration Flyway tự chạy lúc app khởi động.

## Test

```bash
./mvnw test
```

Cần Docker đang chạy (integration test dùng Testcontainers, khởi tạo PostgreSQL thật thay vì mock/H2). Hiện tại: **176 test** (unit + integration), viết song song với từng phase, không dồn cuối.

## API docs

Sau khi chạy app: `http://localhost:8080/swagger-ui.html`

## Observability

- `GET /actuator/health` — public, dùng cho liveness/readiness probe (LB, k8s).
- `GET /actuator/metrics`, `GET /actuator/prometheus` — yêu cầu role `ADMIN`, format Prometheus sẵn sàng scrape bởi Grafana/Prometheus thật.
- Log JSON có cấu trúc (`logstash-logback-encoder`), mỗi request được gắn `requestId` (tự sinh hoặc lấy từ header `X-Request-Id`) vào MDC — cho phép lọc toàn bộ log của 1 request cụ thể khi debug sự cố.

## Điểm nhấn kỹ thuật

- **Trừ kho an toàn dưới concurrency**: conditional `UPDATE ... WHERE available >= :qty` + optimistic lock (`@Version`), không đọc-rồi-ghi — tránh oversell khi nhiều request checkout cùng lúc.
- **Dynamic search/filter**: JPA `Specification` cho `GET /api/products` (category, price range, keyword, rating, in-stock, pagination, sort) thay vì viết N query cho từng tổ hợp filter.
- **Index tuning có đo lường thật**: benchmark bằng `EXPLAIN (ANALYZE, BUFFERS)` trên dataset seed 300k–400k dòng, cải thiện composite index tới **~134×** (product filter+sort), **~42×** (order_items lookup theo seller) — chi tiết + phương pháp đo ở [design doc mục 2](ecommerce-backend-design.md#2-index-strategy-phần-luyện-sql-optimization-trọng-tâm).
- **VNPay integration thật** (không mock field): redirect payment + IPN xử lý async + idempotency (không apply thanh toán 2 lần khi IPN retry) + refund API.
- **Audit trail đầy đủ**: mọi order/delivery status transition ghi vào `order_status_history`/`delivery_status_history`, có `changed_by` phân biệt hành động trực tiếp của actor và hệ quả tự động (auto-cancel, auto-retry).
- **Reporting là exception có chủ đích**: module `report` được phép JOIN trực tiếp qua nhiều bảng/module (chỉ đọc) để tính revenue theo ngày (net, trừ refund) / theo category (gross) và top sản phẩm — thay vì vi phạm ngầm rule module boundary.

## Giới hạn đã biết (v1)

- Coupon/Promotion engine
- Return/Exchange flow
- Seller payout
- Tách shipment theo từng seller trong 1 order
- Notification service (email/push)
- Multi-currency
