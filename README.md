# E-commerce Backend API

[![CI](https://github.com/thvhdvy/ecommerce-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/thvhdvy/ecommerce-backend/actions/workflows/ci.yml)

E-commerce backend written in Java/Spring Boot. The schema, state machines, API contract, and business rules are fully specified in [`ecommerce-backend-design.md`](ecommerce-backend-design.md) — this README is only a navigation summary.

`EXPLAIN ANALYZE` evidence for the indexes added (Phase 5 + the 4 v2 modules): [`docs/query-optimization.md`](docs/query-optimization.md) — including one redundant index found and fixed through actual measurement (`idx_deliveries_order_id`, V25).

Operations runbook for the 5 v2 modules (symptom → diagnosis → remediation, scheduled jobs, current operational tooling limits): [`docs/operations-runbook.md`](docs/operations-runbook.md).

## Architecture

**Modular monolith** — packaged by feature, not by layer at the top level:

`user` · `product` · `inventory` · `cart` · `order` · `payment` · `shipping` · `review` · `report` · `coupon` · `returns` · `notification` · `payout`

(`returns`, not `return` — `return` is a Java keyword; see the rename note in [design doc §8.7](ecommerce-backend-design.md#87-ghi-chú-triển-khai-thực-tế-khác-nhỏ-so-với-bản-thiết-kế-ban-đầu))

Module boundary conventions (details in [design doc §0.8](ecommerce-backend-design.md#08-architecture-decision--modular-monolith-không-phải-microservices)):
- High-churn core transactional modules (Order, Payment, Inventory, Shipping, Coupon redemption, Return, Payout ledger) reference each other by plain ID, **no FK** — even when the target is a foundational module (e.g. `seller_ledger_entries.seller_id` has no FK to `sellers`, even though `sellers` is a foundational module, because the ledger table itself is high-churn financial data — the rule follows the **nature of the table being referenced**, not the target module; see [§9.6](ecommerce-backend-design.md#96-schema)).
- Foundational data modules (User, Seller, Product, Category, Brand) use real FKs.
- A module may only call another module through its service interface — never query another module's repository/entity directly. Two deliberate exceptions:
  - `report` (read-only, joins across many tables to aggregate).
  - `notification` listens to internal domain events (`ApplicationEventPublisher`) instead of being called directly — Order/Payment publish events and never import `NotificationService`, keeping "other modules don't know Notification exists" true.

## Tech stack

| | |
|---|---|
| Language / Framework | Java 21, Spring Boot 4 |
| DB / Migrations | PostgreSQL, Flyway |
| Auth | Spring Security + JWT (access + refresh token) |
| Payment | VNPay sandbox (redirect payment + IPN + refund API, HMAC-SHA512 built from scratch) |
| Email | JavaMailSender + Mailhog (SMTP simulator for dev, see [`docker-compose.yml`](docker-compose.yml)) |
| API docs | springdoc-openapi (Swagger UI) |
| Test | JUnit 5, Mockito, Testcontainers (real PostgreSQL for integration tests) |
| Observability | Spring Boot Actuator + Micrometer/Prometheus, structured JSON logging |
| Container | Docker, Docker Compose |

## Running the app

### 1. Local (Maven)

Requires a running PostgreSQL instance (local or a standalone container).

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
# fill in DB credentials, jwt.secret, VNPay sandbox credentials in the file you just created
./mvnw spring-boot:run
```

### 2. Docker Compose (recommended — no need to install Postgres/SMTP)

```bash
cp .env.example .env
# fill in secrets in .env
docker compose up --build
```

The app runs at `http://localhost:8080`, Postgres is exposed on `5432`, and the Mailhog UI (to see "sent" emails from the Notification module) is at `http://localhost:8025`. Flyway migrations run automatically on app startup.

## Tests

```bash
./mvnw test
```

Requires Docker to be running (integration tests use Testcontainers, spinning up real PostgreSQL instead of mocks/H2). Current count: **299 tests** (unit + integration), written alongside each phase, not batched at the end.

## API docs

Once the app is running: `http://localhost:8080/swagger-ui.html`

## Observability

- `GET /actuator/health` — public, used for liveness/readiness probes (LB, k8s). `management.health.mail.enabled=false` deliberately disables the SMTP health probe — email is a side channel and must not be allowed to drag the whole system `DOWN` when Mailhog is temporarily unreachable.
- `GET /actuator/metrics`, `GET /actuator/prometheus` — require the `ADMIN` role, Prometheus-ready format for scraping by real Grafana/Prometheus.
- Structured JSON logs (`logstash-logback-encoder`), each request tagged with a `requestId` (auto-generated or taken from the `X-Request-Id` header) in MDC — allows filtering all logs for one specific request when debugging an incident.

## Roadmap status

**v1 — complete** (7 phases: foundation → core business logic → payment → fulfillment → search/indexing → reporting → production readiness).

**v2 — 5/5 selected modules complete** (build order: Coupon → Return → Notification → Payout → Shipment-split, prioritizing modules with fewer dependencies/reusable patterns first; see [design doc §5](ecommerce-backend-design.md#5-roadmap)):

| Module | Design doc | Status |
|---|---|---|
| Coupon / Promotion | [§6](ecommerce-backend-design.md#6-v2--module-couponpromotion-phase-8) | Done |
| Return (refund) | [§7](ecommerce-backend-design.md#7-v2--module-returnexchange-phase-9) | Done |
| Notification (email) | [§8](ecommerce-backend-design.md#8-v2--module-notification-phase-10) | Done |
| Seller Payout | [§9](ecommerce-backend-design.md#9-v2--module-seller-payout-phase-11) | Done |
| Shipment per seller | [§10](ecommerce-backend-design.md#10-v2--shipment-theo-từng-seller-phase-12) | Done |
| Multi-currency | [§11](ecommerce-backend-design.md#11-v2--multi-currency-đã-quyết-định-bỏ-khỏi-roadmap) | **Deliberately dropped from the roadmap** — low business/interview value relative to effort for a solo project; prioritized depth on the 5 modules above over breadth |

## Technical highlights

Concurrency, idempotency, and transaction boundaries are the main focus of this project — most bullets below are **the same core technique deliberately reused across different modules**, not accidental duplication.

- **Safe stock deduction under concurrency**: conditional `UPDATE ... WHERE available >= :qty` + optimistic locking (`@Version`), no read-then-write — avoids overselling when multiple checkout requests race. The same two-column (`available`/`reserved`) pattern is reused almost verbatim for **Coupon** (`usage_reserved`/`usage_committed`, [§6.3](ecommerce-backend-design.md#63-business-rules-chốt)) and **seller balance** (conditional `UPDATE balance = balance ± :amount`, [§9.3](ecommerce-backend-design.md#93-business-rules-chốt)).
- **Constraint-first, catch-exception for "N-times-only" race conditions**: a partial unique index (`WHERE status IN (...)`) enforces the rule at the DB layer instead of `SELECT COUNT` then `INSERT` (race-prone — two requests can both read count=0 before either finishes inserting). First used for `payment_webhook_events` (IPN idempotency), reused for **one coupon redemption per user** (`uq_coupon_redemptions_active`) and **"no active return request"** (`return_requests`, [§6.4](ecommerce-backend-design.md#64-concurrency--vì-sao-unique-constraint-thay-vì-đếm-rồi-chèn)/[§7.6](ecommerce-backend-design.md#76-schema)).
- **`REQUIRES_NEW` for every outbound network call made while a lock is held** — a real bug that happened in this repo (fixed in commit `daf5c49`: calling the VNPay refund API while the main transaction still held locks on `orders`/`inventory`). After the fix, the same principle was deliberately reapplied in two new places: `ReturnRefundResultApplier` (calling refund for Return) and `NotificationDispatcher` (sending email must never roll back an order status transition).
- **Outbox pattern for Notification** (instead of calling `JavaMailSender` synchronously inside the request): an internal domain event writes one `notifications` row **in the same transaction** as the order transition (`@TransactionalEventListener(BEFORE_COMMIT)`, no lost events); the actual email send runs as a separate job, retrying with exponential backoff up to `MAX_ATTEMPTS` before marking `FAILED` — an SMTP failure never rolls back core business logic.
- **Aggregate-min status when one order splits into multiple `deliveries`** (Shipment-split, [§10.4](ecommerce-backend-design.md#104-state-machine--aggregate-rule-theo-seller)): `orders.status` is derived by taking the **lowest** rank across sellers (`CONFIRMED < PACKED < SHIPPED < DELIVERED`); sellers that are cancelled/refunded are excluded from the ranked set so they can't permanently lock the aggregate. No intermediate `shipments` table was added — `deliveries` is already the natural "one seller within one order" unit once the old `UNIQUE(order_id)` constraint is dropped.
- **Pro-rated refund by the order's overall discount ratio**: when an order used a coupon, refunding one item/one seller can't use the raw original price — it must be multiplied by `discount_ratio = totalAmount / (totalAmount + discountAmount)` so the sum of `refunds.amount` never exceeds `payments.amount`. One formula, reused unchanged between Return ([§7.3](ecommerce-backend-design.md#73-business-rules-chốt)) and per-seller cancellation ([§10.5.1](ecommerce-backend-design.md#1051-quyết-định-triển-khai-đã-chốt-ngày-2026-08-27)).
- **Running-balance ledger for Payout** instead of a payroll-style "period close" model — a mid-design simplification decision ([§9.1](ecommerce-backend-design.md#91-phạm-vi-v2-số-dư-chạy--running-balance-không-tích-hợp-chuyển-tiền-thật)): the first draft used `period_start`/`period_end`, which forked into 3 different code paths depending on whether a payout was already created/`PENDING`/`PAID` when a Return happened; switching to a single running number (`seller_balances.balance`) reduced Return handling to exactly one action (`recordAdjustment`), no branching needed.
- **Dynamic search/filter**: JPA `Specification` for `GET /api/products` (category, price range, keyword, rating, in-stock, pagination, sort) instead of writing N queries for every filter combination.
- **Index tuning backed by real measurement**: benchmarked with `EXPLAIN (ANALYZE, BUFFERS)` on a 300k–400k row seeded dataset, improving composite indexes by up to **~134×** (product filter+sort) and **~42×** (order_items lookup by seller) — details and methodology in [design doc §2](ecommerce-backend-design.md#2-index-strategy-phần-luyện-sql-optimization-trọng-tâm).
- **Real VNPay integration** (no mocked fields): redirect payment + async IPN handling + idempotency (no double-applying a payment when IPN retries) + refund API.
- **Full audit trail**: every order/delivery/return status transition is written to the corresponding `*_status_history` table, with `changed_by` distinguishing a direct actor action from an automated consequence (auto-cancel, auto-retry, auto-expire, aggregate recompute triggered by independent sellers/shippers).
- **Reporting is a deliberate exception**: the `report` module is allowed to `JOIN` directly across many tables/modules (read-only) to compute revenue by day (net, after refunds) / by category (gross) and top products — instead of silently violating the module-boundary rule.

## Known limitations (deliberate, not oversights)

Every item below is a deliberate scope-narrowing decision with its reasoning recorded in the design doc — not something left undone:

- **Coupon**: at most 1 coupon per order (no stacking), the only eligibility condition is `min_order_amount` (no per-category/per-seller/per-product targeting) — [§6.7](ecommerce-backend-design.md#67-điểm-cần-bạn-quyết-định-lại-nếu-không-đồng-ý).
- **Return**: refund only, no Exchange (swapping for a different product) — comparable complexity to a scaled-down Order module, deferred to v3. No support for returning a partial quantity within one `order_item` — [§7.7](ecommerce-backend-design.md#77-điểm-cần-bạn-quyết-định-lại-nếu-không-đồng-ý).
- **Notification**: email only (no SMS/push), no in-app notification list (read/unread) — [§8.6](ecommerce-backend-design.md#86-điểm-cần-bạn-quyết-định-lại-nếu-không-đồng-ý).
- **Payout**: no real money-transfer integration (admin transfers manually outside the system, then confirms), a fixed commission percentage system-wide (not per-seller/per-category) — [§9.7](ecommerce-backend-design.md#97-điểm-cần-bạn-quyết-định-lại-nếu-không-đồng-ý).
- **Multi-currency**: deliberately dropped from the roadmap, not left unfinished — see the roadmap table above.
