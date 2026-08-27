# E-commerce Backend API — Project Design Doc

Mục tiêu: portfolio project luyện Java/Spring Boot backend, lấp gap kỹ năng cho các vị trí Junior/Fresher Backend Engineer (SQL optimization, RESTful API, clean architecture, production-readiness).

## 0. MVP Scope (v1) — chốt ngày lên kế hoạch

**Nguyên tắc:** thiết kế đơn giản nhất giải quyết đúng yêu cầu hiện tại, nhưng chừa extension point rõ ràng cho hướng mở rộng đã biết (không cố đoán/xây sẵn mọi thứ — xem lý do ở mục Roadmap).

- **Roles (v1, đầy đủ ngay từ đầu):** `CUSTOMER`, `SELLER`, `ADMIN`, `SHIPPER`.
  - CUSTOMER: duyệt sản phẩm, giỏ hàng, đặt hàng, review, xem lịch sử đơn.
  - SELLER: tạo/sửa sản phẩm của chính mình, xem đơn hàng chứa sản phẩm của mình, cập nhật tồn kho. Không có payout/hoa hồng tự động (v2).
  - ADMIN: quản lý user, duyệt sản phẩm/seller, xem báo cáo toàn hệ thống.
  - SHIPPER: nhận đơn được gán, cập nhật trạng thái giao hàng (chỉ đơn của chính mình — row-level authorization).
- **Payment:** tích hợp VNPay (sandbox/test mode) — redirect sang trang thanh toán VNPay, có IPN (Instant Payment Notification, tương đương webhook) xử lý trạng thái thanh toán async, không chỉ mock field.
- **Review/Rating:** có trong v1 (cần cho filter `minRating` và học aggregate `rating_avg`).
- **Model shipment v1 (quyết định kiến trúc quan trọng):** 1 order = 1 shipment duy nhất, **chưa** tách shipment theo từng seller dù order có sản phẩm từ nhiều seller. Lý do: tách theo seller kéo theo bài toán chia nhỏ thanh toán/refund theo từng nhóm — độ phức tạp lớn, để v2.
  - Extension point chừa sẵn: `order_items` có cột `seller_id` (reference) ngay từ v1, dù logic chưa dùng để tách shipment — để khi lên v2 không phải sửa schema gốc, chỉ thêm bảng liên kết qua `seller_id`.
- **Ngoài phạm vi v1 (roadmap v2+):** tách shipment theo seller, seller payout/hoa hồng, multi-currency, coupon/discount engine, return/exchange.

## 0.5 User Flows & Edge Cases (v1)

### Flow 1 — Browse & Search Product
- Sản phẩm bị ẩn/xóa sau khi đã thêm vào giỏ (soft delete, không xóa cứng).
- Sản phẩm hết hàng vẫn xem được chi tiết (chỉ chặn ở bước thêm giỏ/checkout).
- Seller bị khóa/ngừng hoạt động nhưng sản phẩm/order cũ vẫn tồn tại (không cascade xóa).
- Review: chỉ người đã mua mới được review; 1 user không review trùng 1 sản phẩm nhiều lần (unique constraint `user_id + product_id`); review có thể bị ẩn/xóa sau khi đăng (soft delete, ảnh hưởng lại `rating_avg`).

### Flow 2 — Cart & Checkout
- Sản phẩm hết hàng giữa lúc trong giỏ → báo lỗi ở checkout, không giữ chỗ vô thời hạn.
- Giá thay đổi giữa lúc thêm giỏ và checkout → dùng giá tại thời điểm checkout.
- Race condition khi 2 khách cùng đặt sản phẩm chỉ còn 1 tồn kho → pessimistic/optimistic lock khi trừ kho.
- Sản phẩm bị xóa/ẩn khi đang nằm trong giỏ → loại khỏi giỏ, thông báo cho khách.
- Sản phẩm đổi thông tin/variant khi đang trong giỏ → hiển thị cảnh báo, yêu cầu xác nhận lại trước khi checkout.
- Số lượng trong giỏ vượt giới hạn mua tối đa/sản phẩm → chặn ở validation.
- Coupon hết hạn/không hợp lệ lúc checkout → đã quyết định KHÔNG build coupon ở v1 (xem Roadmap), case này chỉ ghi nhận cho v2.
- User mở nhiều tab/device cùng checkout 1 giỏ → cần idempotency ở tầng tạo order (không tạo 2 order từ cùng 1 giỏ).
- Checkout thất bại giữa chừng → không được tạo order ở trạng thái dở dang; dùng transaction, rollback toàn bộ nếu có bước lỗi.

### Flow 3 — Payment (VNPay)
- Thanh toán thành công nhưng frontend không nhận được kết quả (Return URL) → nguồn sự thật (source of truth) là **IPN** (Instant Payment Notification, server-to-server) từ VNPay, không phải Return URL/response phía client — Return URL chỉ để hiển thị tạm, có thể bị user tắt tab hoặc giả mạo param.
- User refresh/đóng tab rồi mở lại trang payment → order giữ trạng thái `PENDING_PAYMENT`, cho phép query lại trạng thái thật (từ DB, đã được đồng bộ qua IPN).
- Thanh toán thành công nhưng IPN đến sau → order tạm thời vẫn `PENDING_PAYMENT` cho đến khi IPN xử lý xong.
- IPN bị gửi trùng (VNPay retry nếu không nhận được response `{RspCode, Message}` đúng format) → không xử lý thanh toán 2 lần (kiểm tra trạng thái order trước khi apply, xem idempotency ở mục "Payment (chốt riêng)").
- Payment amount không khớp order amount → từ chối, đánh dấu order cần review thủ công (không tự động confirm).
- Payment bị hoàn tiền một phần → **không** phải order status riêng; được quản lý hoàn toàn bởi module Payment/Refund qua bảng `refunds` (so sánh tổng `refunds.amount` với `payments.amount`). Order vẫn giữ status hiện tại (VD: `COMPLETED`), không có nhánh `PARTIALLY_REFUNDED` trong state machine order.
- Payment timeout/expired → tự động chuyển `PAYMENT_EXPIRED` sau 15 phút, giải phóng tồn kho đã giữ chỗ.

### Flow 4 — Shipping & Delivery
- Order nhiều seller nhưng v1 chỉ có 1 shipment/order (đã chốt ở MVP Scope) — case "nhiều shipment/order" ghi nhận cho v2.
- Shipper được gán nhưng sau đó unavailable → admin gán lại shipper khác.
- Shipper nhận order nhưng không thể giao → cập nhật trạng thái `FAILED_DELIVERY`, kèm lý do (`failure_reason`).
- Khách không nghe máy/không có mặt → `FAILED_DELIVERY`, cho phép retry theo chính sách (v1: tối đa 1 lần retry).
- Khách từ chối nhận hàng → ghi nhận qua `failure_reason = CUSTOMER_REJECTED`, luồng dẫn tới refund.
- Giao thành công nhưng hệ thống không nhận được cập nhật → cần cơ chế đối soát thủ công (admin xác nhận qua báo cáo từ shipper).
- Cập nhật trạng thái không đúng thứ tự (VD: SHIPPER báo DELIVERED khi chưa được ASSIGNED) → validate transition hợp lệ ở tầng service, từ chối transition sai.

### Flow 5 — Cancel & Refund
- Khách hủy đơn ở trạng thái nào được phép: chỉ `PENDING_PAYMENT`/`CONFIRMED`, không cho hủy khi đã `PACKED` trở đi.
- Khách yêu cầu hủy nhưng seller/admin đã bắt đầu xử lý (đã `PACKED`) → cần ADMIN force-cancel, không tự động.
- Hủy order sau khi đã thanh toán → trigger refund qua VNPay Refund API.
- Refund thất bại → **refund** (dòng trong bảng `refunds`) giữ trạng thái `REFUND_FAILED`, cần admin can thiệp thủ công. Đây là trạng thái của `refunds.status`, **không phải** `orders.status` (order vẫn giữ status hiện tại, xem Flow 3).
- Refund IPN đến trễ/trùng → cùng nguyên tắc Flow 3 (IPN là nguồn sự thật, chống xử lý trùng).
- Refund một phần order — hỗ trợ qua bảng `refunds` (nhiều dòng refund cộng dồn); UI/luồng chi tiết cho "hủy 1 item trong order nhiều item" để v2 (liên quan trực tiếp tới việc chưa tách shipment theo seller).

### Flow 6 — Authentication & Account
- Đăng ký, đăng nhập/đăng xuất (JWT access + refresh token).
- Quên/reset mật khẩu qua email.
- Email verification khi đăng ký (v1: chỉ cảnh báo, không chặn cứng).
- Tài khoản bị khóa/vô hiệu hóa bởi admin → JWT hiện có bị vô hiệu (check trạng thái user mỗi request qua filter, kết hợp revoke `refresh_tokens`).
- Đăng nhập nhiều thiết bị → cho phép (không giới hạn số refresh token đồng thời ở v1).
- User cố truy cập tài nguyên không thuộc quyền → 403, kiểm tra ownership ở tầng service (không chỉ role).

### Flow 7 — Seller Management
- Seller tạo/sửa/ẩn sản phẩm (soft delete qua `status`), cập nhật tồn kho.
- Seller xem và xử lý order chứa sản phẩm của mình.
- Seller bị khóa nhưng sản phẩm/order cũ vẫn tồn tại (đồng nhất với Flow 1).
- Seller cố thao tác với sản phẩm/order không thuộc mình → 403, kiểm tra ownership.

### Flow 8 — Promotion/Coupon [KHÔNG build ở v1]
Ghi nhận là roadmap v2. Không thiết kế chi tiết ở giai đoạn này để tránh việc build engine coupon phức tạp (loại giảm giá, điều kiện áp dụng, giới hạn lượt dùng có concurrency, stacking rule) làm phình scope v1.

### Flow 9 — Return/Exchange [KHÔNG build ở v1]
Ghi nhận là roadmap v2. Lý do: kéo theo chồng thêm 1 state machine mới lên order/refund vốn đã phức tạp (Flow 3, 5). Không thiết kế chi tiết ở v1.

### Flow 10 — Admin Management
- Quản lý user, seller, sản phẩm, order.
- Quản lý refund (duyệt case `REFUND_FAILED` cần can thiệp thủ công).
- Quản lý review (ẩn review vi phạm).
- Khóa/mở khóa tài khoản (user, seller).
- Can thiệp vào dispute/order có vấn đề (payment amount mismatch, đối soát giao hàng không rõ trạng thái).

## 0.6 Business Rules (v1)

### Order status — state machine chốt

```
PENDING_PAYMENT
 ├── PAYMENT_FAILED ──→ PENDING_PAYMENT   (retry thanh toán, không tạo order mới)
 ├── PAYMENT_EXPIRED                      (terminal — quá 15 phút không thanh toán)
 └── CANCELLED                            (không refund — chưa thanh toán thành công)

PENDING_PAYMENT → CONFIRMED               (thanh toán thành công)

CONFIRMED
 ├── CANCELLED → REFUND_PENDING → REFUNDED / REFUND_FAILED
 └── PACKED → SHIPPED

SHIPPED
 ├── DELIVERED → COMPLETED     (tự động sau 3 ngày nếu không phát sinh return/dispute;
 │                              customer có thể tự xác nhận sớm hơn — hỗ trợ nhưng không bắt buộc ở MVP)
 └── FAILED_DELIVERY
       ├── RETRY → SHIPPED                (tối đa 1 lần)
       └── CANCELLED → REFUND_PENDING → REFUNDED / REFUND_FAILED   (lần thất bại thứ 2 → auto-cancel + refund)
```

Quy tắc nền: `RETRY` và lý do delivery thất bại (khách không nghe máy / từ chối nhận...) **không phải Order status** — status chỉ có `FAILED_DELIVERY`, chi tiết lý do lưu ở field `failure_reason` riêng trong module Shipping (enum), tránh phình status cấp cao.

**Payment status (chốt riêng, thuộc module Payment — enum của bảng `payments`, khác với `orders.status`):** `PENDING`, `SUCCEEDED`, `FAILED` (tạm thời, cho phép order quay lại `PENDING_PAYMENT` để retry — không bắt khách tạo order mới), `EXPIRED` (hết 15 phút, terminal). Đây là enum nội bộ module Payment; `orders.status` (`PENDING_PAYMENT`/`PAYMENT_FAILED`/`PAYMENT_EXPIRED`/`CONFIRMED`) là trạng thái nghiệp vụ cấp order, được đồng bộ từ payment status qua service call, không phải cùng 1 enum.

**Auto-complete rule:** `DELIVERED → COMPLETED` tự động sau 3 ngày nếu không phát sinh return/dispute (v2 mới có return, nhưng field/hook nên chừa sẵn). Customer có thể tự xác nhận nhận hàng sớm hơn — hỗ trợ nhưng không bắt buộc ở MVP.

**Review eligibility:** cho phép review khi `order_item` tương ứng đã ở trạng thái DELIVERED. Vì v1 chỉ có 1 shipment/order (giao toàn bộ cùng lúc), "order_item DELIVERED" được suy ra từ `orders.status = DELIVERED`/`COMPLETED` (không cần thêm cột riêng ở `order_items` cho v1) — mọi item trong order đủ điều kiện review cùng lúc. Khi v2 tách shipment theo seller, lúc đó mới cần trạng thái delivered thật sự ở cấp item.

**Refund rule (áp dụng toàn hệ thống):** bất kỳ cancellation nào xảy ra **sau khi thanh toán thành công** đều bắt buộc đi qua `REFUND_PENDING → REFUNDED`/`REFUND_FAILED`. Cancellation xảy ra **trước khi** thanh toán thành công (từ `PENDING_PAYMENT`) không cần refund. `REFUND_FAILED` không đồng nghĩa "đã hủy xong về tài chính" — nghĩa là quyết định hủy đã có nhưng tiền chưa hoàn tất, cần retry/can thiệp thủ công (ADMIN).

### Timeout & retry

| Rule | Giá trị |
|---|---|
| Payment timeout | 15 phút → `PAYMENT_EXPIRED`, giải phóng reservation tồn kho |
| Delivery retry tối đa | 1 lần |
| Retry thất bại lần 2 | Auto-cancel + refund (nếu đã thanh toán) |

### Cancel policy (theo trạng thái, không theo mốc thời gian)

- `PENDING_PAYMENT`, `CONFIRMED`: customer được tự hủy.
- `PACKED` trở đi: customer **không** được tự hủy — chỉ ADMIN force-cancel (vẫn theo refund rule ở trên).
- `SHIPPED`/`DELIVERED`/`COMPLETED`: không cancel — dùng Return/Refund flow (v2, ngoài phạm vi v1).

### Ownership & data visibility

- **SELLER** chỉ xem thông tin cần cho fulfillment của order item thuộc seller đó: tên người nhận, số điện thoại, địa chỉ giao hàng, ghi chú giao hàng. **Không** xem email tài khoản, thông tin auth, hay dữ liệu cá nhân không liên quan fulfillment.
- **ADMIN** được thực hiện các action giới hạn: cancel order, trigger/retry refund, assign/reassign shipper, xử lý failed delivery, force transition khi cần hỗ trợ. **Không** được sửa trực tiếp dữ liệu lịch sử (giá sản phẩm trong order, quantity đã mua, payment amount, seller của order item) — cần correction thì tạo adjustment/audit action riêng, không ghi đè lịch sử gốc.

### Multi-seller trong 1 order (v1 = 1 shipment/order)

- `CONFIRMED → PACKED` (order-level) chỉ xảy ra khi **100% `order_items` đã `item_status = PACKED`** (aggregate rule).
- Seller chỉ được pack `order_item` thuộc seller của chính mình (ownership check).
- Với v1, seller "chậm nhất" quyết định thời điểm cả order sẵn sàng ship — chấp nhận đánh đổi này, cải thiện (tách shipment theo seller) để ở v2.
- Mọi order status transition (không chỉ do ADMIN, kể cả tự động do timeout/webhook) đều phải ghi vào `order_status_history`.

### Payment (chốt riêng)

- `payments.amount` = tổng tiền phải thanh toán của order tại thời điểm tạo payment request tới VNPay, không lấy lại giá product hiện tại, không đổi sau khi đã tạo (trừ khi tạo lại payment request mới). Số tiền VNPay xác nhận trong IPN (`vnp_Amount`, chia lại 100) phải khớp `payments.amount` — nếu không khớp, từ chối và đánh dấu cần review thủ công.
- Tổng `refunds.amount` (trạng thái `REFUND_PENDING`/`REFUNDED`) của 1 `payment` không được vượt quá `payments.amount`. Tổng bằng đúng `payments.amount` → full refund; nhỏ hơn → payment coi như partially refunded (đọc từ `refunds`, **không** phải `orders.status` — xem Flow 3).
- IPN idempotency: check `vnp_TransactionNo` (mã giao dịch phía VNPay) đã tồn tại chưa rồi mới xử lý — bước "check + lưu event + đánh dấu processed" phải nằm trong **cùng 1 transaction** (hoặc `INSERT ... ON CONFLICT DO NOTHING` trên cột UNIQUE rồi kiểm tra số dòng ảnh hưởng), tránh 2 IPN trùng đến gần như đồng thời cùng vượt qua bước check riêng lẻ. IPN handler phải trả về đúng format `{RspCode, Message}` mà VNPay yêu cầu, nếu không VNPay sẽ tự động gửi lại.

## 0.7 API Endpoint Structure (chốt — chưa gồm request/response DTO)

```
AUTH
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/refresh
POST   /api/auth/logout
POST   /api/auth/forgot-password
POST   /api/auth/reset-password

USER
GET    /api/users/me
PATCH  /api/users/me

PRODUCT
GET    /api/products              (filter: ?category=&seller=&minPrice=&maxPrice=&keyword=&minRating=&inStockOnly=)
GET    /api/products/{id}
GET    /api/products/{id}/reviews
POST   /api/products/{id}/reviews

SELLER PRODUCT
POST   /api/seller/products
PATCH  /api/seller/products/{id}
PATCH  /api/seller/products/{id}/status      (ACTIVE / INACTIVE — soft delete, không dùng DELETE)
PATCH  /api/seller/products/{id}/inventory

CART
GET    /api/cart
POST   /api/cart/items
PATCH  /api/cart/items/{id}
DELETE /api/cart/items/{id}

ORDER
POST   /api/orders/checkout
GET    /api/orders
GET    /api/orders/{id}
POST   /api/orders/{id}/cancel

PAYMENT
POST   /api/payments/{orderId}/intent  (trả về vnp_PaymentUrl để FE redirect user sang VNPay)
GET    /api/payments/{orderId}         (query trạng thái thật — dùng khi refresh/mất response/IPN trễ)
GET    /api/payments/vnpay-ipn         (VNPay IPN — gửi qua GET query params theo spec VNPay, verify vnp_SecureHash, không dùng JWT)

SELLER FULFILLMENT
GET    /api/seller/orders
PATCH  /api/seller/order-items/{orderItemId}/status

SHIPPER
GET    /api/shipper/deliveries
PATCH  /api/shipper/deliveries/{deliveryId}/status

ADMIN
GET    /api/admin/users
PATCH  /api/admin/users/{id}/lock
GET    /api/admin/sellers
PATCH  /api/admin/sellers/{id}/lock
GET    /api/admin/orders
POST   /api/admin/orders/{id}/cancel
POST   /api/admin/orders/{id}/refund/retry
PATCH  /api/admin/orders/{id}/assign-shipper
GET    /api/admin/reports/revenue-by-day        (?from=&to=, net revenue = gross - refund)
GET    /api/admin/reports/revenue-by-category    (?from=&to=, gross revenue)
GET    /api/admin/reports/top-products           (?from=&to=&limit=, gross revenue, sort desc)
```

Chưa có endpoint cho Coupon/Return-Exchange/Notification — đúng theo quyết định roadmap v2 (mục 0.5).

## 0.8 Architecture Decision — Modular Monolith (không phải Microservices)

**Quyết định:** triển khai dưới dạng modular monolith — 1 ứng dụng Spring Boot duy nhất, chia module rõ theo domain, thay vì tách thành các microservices độc lập.

**Bối cảnh:** có cân nhắc phương án microservices (API Gateway + nhiều service riêng: User/Auth, Product, Cart, Order, Payment, Inventory, Shipping, Notification — mỗi service 1 database riêng, giao tiếp qua API/event).

**Lý do không chọn microservices cho project này:**
- Mục tiêu gốc của project là luyện Spring Boot/Java backend tập trung, không lan man công nghệ — microservices kéo theo hạ tầng lớn (service discovery, message broker, distributed tracing, orchestration) không phục vụ mục tiêu đó.
- Mục tiêu tuyển dụng (Junior/Fresher Backend) không yêu cầu kinh nghiệm microservices — đây là chủ đề mid/senior.
- Không có lý do tổ chức thật (nhiều team độc lập, nhu cầu scale khác nhau) để đánh đổi chi phí vận hành phân tán — dev một mình.
- Chi phí kỹ thuật cụ thể: business rule "trừ kho atomic khi đặt hàng" chỉ là 1 DB transaction đơn giản trong monolith; nếu tách Order/Inventory ra 2 database riêng sẽ thành distributed transaction (Saga, compensating transaction, eventual consistency) — độ khó tăng vọt, rủi ro cao cho 1 project luyện tập.

**Module boundaries giữ lại (tinh thần tốt từ đề xuất microservices, áp dụng trong monolith):**

```
com.example.ecommerce
├── common/          (exception handler, response envelope, pagination DTO)
├── config/          (SecurityConfig, OpenApiConfig, JpaConfig)
├── security/         (JWT filter, UserDetailsService)
├── user/              (Auth, User, Seller identity, ADMIN lock user/seller)
├── product/           (Product, Category, Brand, seller quản lý sản phẩm, ACTIVE/INACTIVE)
├── inventory/          (tồn kho — tách khỏi product module dù cùng DB, để boundary rõ)
├── cart/
├── order/              (Order, OrderItem — chỉ giữ reference id + snapshot, không sở hữu product/inventory/payment data)
├── payment/            (VNPay integration, payment status, refund, IPN)
├── shipping/            (shipper assignment, delivery status, failure reason)
├── review/
└── report/              (aggregation: doanh thu theo ngày/tháng, top sản phẩm bán chạy)
```

Mỗi module có layer nội bộ riêng (`controller/service/repository/dto/entity`) — feature-based package, không phải layer-based ở top level. Lý do: khi thêm tính năng mới, chỉ cần touch 1 module thay vì rải rác nhiều thư mục — dễ maintain, đúng nguyên tắc SOLID (Single Responsibility ở cấp module).

**Nguyên tắc module (áp dụng ngay từ đầu để giữ optionality tách microservices sau này nếu thật sự cần):**
- Mỗi module chỉ truy cập dữ liệu module khác qua **service interface** (Java interface), không query trực tiếp repository/entity của module khác.
- **Quy tắc FK xuyên module (áp dụng nhất quán toàn schema):** module thuộc nhóm giao dịch cốt lõi, biến động cao, khả năng tách service độc lập trong tương lai (Order, Payment, Inventory, Shipping) → tham chiếu chéo giữa các module này **không dùng DB FK**, chỉ business reference (+ snapshot khi cần lịch sử). Module thuộc nhóm dữ liệu nền/tương đối tĩnh (User, Seller, Product, Category, Brand) → vẫn dùng FK thật vì toàn vẹn dữ liệu có giá trị và nhóm này khó/không cần tách rời. Ví dụ: `payments.order_id`, `refunds.order_id`, `deliveries.order_id`, `order_items.product_id/seller_id` → không FK; `products.seller_id`, `deliveries.shipper_id` → vẫn FK thật.
- 1 database PostgreSQL duy nhất, schema/table nhóm rõ theo module ownership (xem mục 0.9).
- Giao tiếp giữa module: gọi service trực tiếp (in-process, không phải network call) — không cần message broker. Muốn luyện tư duy event-driven vẫn có thể dùng Spring's `ApplicationEventPublisher` cho side-effect nội bộ (VD: đồng bộ `deliveries.status` → `orders.status`), không phải Kafka/RabbitMQ.
- Report module là ngoại lệ hợp lý duy nhất được phép query trực tiếp qua nhiều bảng của nhiều module (chỉ đọc, không ghi).

**Roadmap:** nếu sau này project thật sự cần tách microservices (mục đích học tập riêng), module boundaries đã rõ ràng ở đây chính là ranh giới để tách — nhưng đó là quyết định của một project/giai đoạn khác, không phải v1 này.

## 0.9 Database Schema per Module (Modular Monolith — 1 DB, tách theo ownership)

### Module: User / Auth

```
users
- id (PK), email (unique), password_hash, full_name, phone (nullable)
- role (enum: CUSTOMER, ADMIN, SHIPPER)
- status (ACTIVE, LOCKED)
- email_verified (boolean), created_at, updated_at

sellers
- id (PK), user_id (FK → users, UNIQUE — quan hệ 1-1)
- store_name, store_description (nullable)
- status (ACTIVE, LOCKED)              -- lock độc lập với users.status
- created_at, updated_at

refresh_tokens
- id (PK), user_id (FK), token_hash, device_info (nullable), expires_at, revoked (boolean), created_at

password_reset_tokens
- id (PK), user_id (FK), token_hash, expires_at, used (boolean)
```
Ghi chú: `role = CUSTOMER` là mặc định; một CUSTOMER có thể có thêm 1 dòng `sellers` để trở thành người bán (cộng thêm khả năng, không đổi role). `ADMIN`/`SHIPPER` là role hệ thống, gán bởi quản trị viên.

### Module: Product

```
categories
- id (PK), name, parent_id (nullable, self-FK — category lồng nhau)

brands
- id (PK), name

products
- id (PK), seller_id (FK → sellers.id), category_id (FK → categories.id), brand_id (FK, nullable)
- name, description, price
- status (ACTIVE, INACTIVE)            -- soft delete/hide
- rating_avg (denormalized, cập nhật bởi module Review)
- in_stock (denormalized, boolean, default false — cập nhật bởi module Inventory qua `ProductService.updateStockFlag()` mỗi khi `inventory.quantity_available` đổi do reserve/release/seller cập nhật tay; phục vụ filter `inStockOnly` ở `GET /api/products` mà không cần Product module query thẳng bảng `inventory`, giữ đúng nguyên tắc module boundary)
- created_at, updated_at

product_images
- id (PK), product_id (FK), url, is_primary (boolean)
```

### Module: Inventory (tách riêng khỏi Product, ownership khác)

```
inventory
- id (PK), product_id (FK → products.id, UNIQUE — 1 sản phẩm = 1 dòng tồn kho ở v1)
- quantity_available
- quantity_reserved
- version (optimistic lock — @Version JPA)
- updated_at
```

**State transition `available`/`reserved` (chốt):**

| Event | Available | Reserved |
|---|---|---|
| Checkout → `PENDING_PAYMENT` | ↓ | ↑ |
| `PAYMENT_FAILED` → retry (vẫn `PENDING_PAYMENT`) | — | — |
| Payment success → `CONFIRMED` | — | ↓ (coi như đã bán, không cộng lại available) |
| `PAYMENT_EXPIRED` | ↑ (trả lại) | ↓ |
| Cancel trước payment (`PENDING_PAYMENT → CANCELLED`) | ↑ | ↓ |
| Cancel sau payment (`CONFIRMED`/`FAILED_DELIVERY → CANCELLED`) | Không tự tăng | Đã về 0 từ bước CONFIRMED |

Lý do chọn cơ chế 2 cột `available`/`reserved` thay vì chỉ trừ thẳng `available`: khớp với business rule timeout 15 phút + cho phép retry payment trên cùng order, đồng thời phân biệt rõ "hàng đang giữ chỗ" và "hàng đã bán". Việc nhập lại kho khi return/refund (v2) sẽ là luồng riêng, không tự động qua cơ chế cancel này.

**Lưu ý implementation (ghi nhớ cho lúc code):** thao tác `available↓/reserved↑` phải là 1 câu `UPDATE ... SET available = available - :qty, reserved = reserved + :qty WHERE product_id = :id AND available >= :qty` — điều kiện `WHERE` chống race condition ở tầng DB; cột `version` chỉ là lớp bảo vệ optimistic lock bổ sung, không thay thế điều kiện này.

### Module: Cart

```
carts
- id (PK), user_id (FK → users, UNIQUE — mỗi user 1 giỏ hàng active)
- created_at, updated_at

cart_items
- id (PK), cart_id (FK), product_id (FK → products.id)
- quantity
- created_at, updated_at
```
Không lưu snapshot giá — giỏ hàng luôn hiển thị giá hiện tại; giá chỉ "đóng băng" khi tạo order (đúng edge case: giá đổi giữa lúc thêm giỏ và checkout → dùng giá tại thời điểm checkout).

### Module: Order

```
orders
- id (PK), customer_id (FK → users)
- status (enum: PENDING_PAYMENT, PAYMENT_FAILED, PAYMENT_EXPIRED, CONFIRMED,
           PACKED, SHIPPED, DELIVERED, COMPLETED, CANCELLED, FAILED_DELIVERY)
- total_amount
- shipping_recipient_name, shipping_phone, shipping_address, shipping_note   -- snapshot lúc đặt hàng
- created_at, updated_at

order_items
- id (PK), order_id (FK)
- product_id (reference, không FK), seller_id (reference, không FK)
- product_name_snapshot, unit_price_snapshot, quantity
- item_status (enum: PENDING, PACKED)

order_status_history
- id (PK), order_id (FK), from_status, to_status
- changed_by (FK → users, nullable — null nếu hệ thống tự động, VD timeout)
- reason (nullable)
- created_at
```

**Quy ước `changed_by` (áp dụng cả `order_status_history` và `delivery_status_history`):** ghi actor thực hiện hành động **trực tiếp** gây ra transition (customer cancel, seller pack, admin assign/reassign shipper, shipper cập nhật delivery status) — kể cả khi transition đó được đồng bộ qua service call xuyên module (VD: admin assign shipper → `ShippingService` gọi `OrderService.markShipped(orderId, actor)`, actor vẫn là admin đó, không phải null). Chỉ để `null` cho các entry là **hệ quả tự động của business rule**, không gắn với 1 hành động cụ thể của actor tại thời điểm đó — VD: payment timeout worker, entry auto-retry (`FAILED_DELIVERY → SHIPPED`), entry auto-cancel sau khi hết quyền retry (`FAILED_DELIVERY → CANCELLED`), scheduled job auto-complete (`DELIVERED → COMPLETED`). Với các entry auto-retry/auto-cancel, dù nguyên nhân sâu xa là shipper report FAILED, quyết định retry/cancel là do business rule (đếm `retry_count`) tự quyết, không phải shipper trực tiếp chọn — nên để `null`; riêng entry `SHIPPED → FAILED_DELIVERY` (báo cáo thất bại) vẫn ghi shipper vì đó là báo cáo trực tiếp của họ.

Không có field payment (`refunded_amount`...) trong `orders` — thuộc module Payment (ownership tách riêng). Xem "Multi-seller trong 1 order" ở mục 0.6 cho aggregate rule `CONFIRMED → PACKED`.

### Module: Payment

```
payments
- id (PK), order_id (reference, không FK — UNIQUE, 1 payment gốc/order ở v1)
- vnp_txn_ref (mã giao dịch phía merchant tự sinh, gửi trong request tạo payment)
- vnp_transaction_no (mã giao dịch phía VNPay trả về, nullable tới khi có IPN)
- amount
- status (enum: PENDING, SUCCEEDED, FAILED, EXPIRED)   -- nguồn sự thật cho trạng thái thanh toán
- created_at, updated_at

refunds
- id (PK), payment_id (FK → payments), order_id (reference, không FK)
- amount
- status (enum: REFUND_PENDING, REFUNDED, REFUND_FAILED)
- vnp_refund_transaction_no (nullable)
- reason (nullable)
- created_at, updated_at

payment_webhook_events
- id (PK), vnp_transaction_no (UNIQUE — chống xử lý trùng IPN)
- event_type
- payload (raw JSON, phục vụ debug/audit)
- processed_at
```

Xem business rule chi tiết (amount immutable, tổng refund giới hạn, webhook idempotency trong transaction) ở mục 0.6.

### Module: Shipping

```
deliveries
- id (PK), order_id (reference, không FK — UNIQUE, 1:1 order↔delivery ở v1)
- shipper_id (FK → users.id, nullable — null đến khi được gán)
- status (enum: ASSIGNED, IN_TRANSIT, DELIVERED, FAILED)
- failure_reason (enum, nullable: CUSTOMER_UNREACHABLE, CUSTOMER_REJECTED, ADDRESS_ISSUE, OTHER)
- retry_count (int, default 0)          -- tối đa 1 lần retry
- assigned_at, picked_up_at (nullable), delivered_at (nullable)
- created_at, updated_at

delivery_status_history
- id (PK), delivery_id (FK → deliveries), from_status, to_status
- changed_by (FK → users, nullable — null nếu hệ thống tự động)
- reason (nullable)
- created_at
```

Ghi chú FK: `shipper_id → users.id` dùng FK thật (User = nhóm dữ liệu nền); `order_id` không FK (Order = nhóm giao dịch cốt lõi). Khi `deliveries.status` đổi, đồng bộ ngược `orders.status` (SHIPPED→DELIVERED/FAILED_DELIVERY) qua service call nội bộ giữa 2 module — không dùng DB trigger xuyên module.

### Module: Review

```
reviews
- id (PK), product_id (FK → products.id), user_id (FK → users.id)
- order_id (reference, không FK — dùng để verify "chỉ người đã mua mới được review")
- rating (1-5), comment (nullable)
- status (VISIBLE, HIDDEN)          -- admin ẩn review vi phạm, không xóa cứng
- created_at, updated_at

UNIQUE (product_id, user_id)        -- 1 user không review trùng 1 sản phẩm
```
`rating_avg` trên `products` được tính lại ở tầng service (`ProductService.recalculateRating()` gọi từ Review module sau khi tạo/ẩn review) — không dùng DB trigger xuyên module.

### Module: Report

Không có bảng riêng — chỉ là các aggregation query (revenue theo ngày/tháng từ `orders`+`payments` đã `CONFIRMED` trở lên, top sản phẩm từ `order_items`). Đây là ngoại lệ hợp lý duy nhất được phép query trực tiếp qua nhiều bảng của nhiều module (chỉ đọc, không ghi) — khác với rule "không JOIN chéo module" vốn áp dụng cho nghiệp vụ ghi/giao dịch.

**Các quyết định chốt khi triển khai (Phase 6):**
- **Revenue theo ngày**: bucket theo ngày order thực sự chuyển `CONFIRMED` (lấy `order_status_history.created_at` WHERE `to_status='CONFIRMED'`, không phải `orders.created_at` lúc còn `PENDING_PAYMENT`). Mỗi order chỉ có đúng 1 dòng `to_status=CONFIRMED` trong lịch sử (state machine không quay lại `PENDING_PAYMENT` sau khi đã confirm) nên không có rủi ro double-count.
- **Revenue theo ngày là NET**: `net = gross (SUM orders.total_amount) - refund (SUM refunds.amount WHERE status=REFUNDED)`. Refund được trừ vào đúng ngày order được confirm (không phải ngày refund xảy ra) — đơn giản hoá vì 1 order chỉ ghi nhận doanh thu 1 lần, chấp nhận đánh đổi nhỏ nếu refund xảy ra khác ngày với lúc confirm.
- **Revenue theo category và Top sản phẩm là GROSS** (không trừ refund): do `refunds` chỉ lưu số tiền ở mức order/payment, không có breakdown theo từng `order_item`, nên không thể xác định chính xác refund thuộc category/sản phẩm nào khi 1 order có nhiều loại sản phẩm. Prorate theo tỷ lệ giá trị item bị cân nhắc nhưng bỏ qua vì thêm độ phức tạp không cần thiết cho một con số thống kê — ghi nhận đây là giới hạn đã biết của v1.
- Cả 3 query đều lọc order qua điều kiện "đã từng có `to_status=CONFIRMED`" — tự động loại các order `PENDING_PAYMENT`/`PAYMENT_FAILED`/`PAYMENT_EXPIRED`/`CANCELLED` (huỷ trước khi confirm) khỏi doanh thu; order confirm rồi mới bị huỷ vẫn tính gross ở ngày confirm nhưng net về ~0 nếu refund full.
- Kỹ thuật: `ReportQueryRepository` (không extend `JpaRepository`, chỉ `Repository<Order,Long>` marker) dùng native `@Query` + interface projection — vì aggregation phức tạp (nhiều JOIN, `GROUP BY DATE(...)`) viết bằng SQL rõ ràng hơn JPQL.

## 1. Tech stack

- Java 17+, Spring Boot 3.x
- Spring Data JPA + PostgreSQL
- Spring Security + JWT (auth theo role: CUSTOMER, ADMIN, SHIPPER + seller identity riêng)
- Flyway (database migration, versioned schema — không dùng `hibernate.ddl-auto=update` cho production-like habit)
- Spring Validation (Bean Validation cho DTO)
- Springdoc OpenAPI (Swagger UI tự sinh docs)
- JUnit 5 + Mockito (unit test), Testcontainers (integration test với PostgreSQL thật)
- Spring Boot Actuator (health check, metrics — tập dượt phần "theo dõi hệ thống" trong JD)
- Docker + docker-compose (app + PostgreSQL)
- VNPay Payment API (sandbox/test mode) — không có SDK Java chính thức, tự build request/verify HMAC-SHA512 — redirect payment + IPN xử lý trạng thái thanh toán async

## 2. Index strategy (phần luyện SQL optimization trọng tâm)

- `products(seller_id)`, `products(category_id)`, `products(brand_id)` — filter phổ biến.
- Composite index `products(category_id, price)` — hỗ trợ filter theo category + sort/range theo giá cùng lúc.
- `orders(customer_id, created_at DESC)` — truy vấn lịch sử đơn hàng.
- `orders(status)` — hỗ trợ job quét `PENDING_PAYMENT` quá hạn (payment timeout worker).
- `inventory(product_id)` unique index (đã có qua constraint).
- `order_items(order_id)`, `order_items(seller_id)` — seller truy vấn order của mình.
- `payments(order_id)` unique, `refunds(payment_id)`.
- `deliveries(shipper_id)`, `deliveries(order_id)` unique.
- Cân nhắc GIN + `pg_trgm` cho tìm kiếm tên sản phẩm dạng LIKE '%keyword%'.
- Dùng `EXPLAIN ANALYZE` để so sánh trước/sau khi thêm index — ghi lại kết quả, đây là bằng chứng cụ thể để kể khi phỏng vấn.

### Kết quả benchmark (thực hiện `V13__add_search_and_query_indexes.sql`)

Setup: Postgres 18 (Docker riêng, không phải DB app), seed 300k `products` / 200k `orders` / 400k `order_items` bằng dữ liệu random, `ANALYZE` trước mỗi lần đo. Đo bằng `EXPLAIN (ANALYZE, BUFFERS)`, KHÔNG dùng `EXPLAIN` suông (tránh dựa vào cost ước lượng, đo bằng execution time + buffer thật).

| Query | Trước (chỉ có index đơn cột) | Sau (composite/GIN) | Cải thiện |
|---|---|---|---|
| `products WHERE category_id=? AND price BETWEEN ? AND ? ORDER BY price LIMIT 20` | 21.5 ms — Bitmap Heap Scan trên `idx_products_category_id` rồi filter `price` bằng tay + sort riêng (5591 block đọc) | 0.16 ms — Index Scan thẳng trên `idx_products_category_id_price`, không cần sort riêng (index đã đúng thứ tự) | **~134×** |
| `products WHERE name ILIKE '%laptop%'` (đếm toàn bộ, không LIMIT) | 67 ms — Parallel Seq Scan toàn bảng 300k dòng | 28.6 ms — Bitmap Heap Scan qua `idx_products_name_trgm` (GIN + `pg_trgm`) | ~2.3× (từ khoá match ~10% bảng nên GIN chưa tối ưu hết mức — với từ khoá hiếm hơn mức cải thiện sẽ lớn hơn nhiều, vì GIN loại được phần lớn heap block không khớp) |
| `orders WHERE customer_id=? ORDER BY created_at DESC LIMIT 20` | 0.27 ms — Bitmap Heap Scan trên `idx_orders_customer_id` + sort riêng | 0.077 ms — Index Scan thẳng trên `idx_orders_customer_id_created_at`, không sort riêng | ~3.5× |
| `order_items WHERE seller_id=?` (đếm toàn bộ) | 13.4 ms — Parallel Seq Scan toàn bảng 400k dòng | 0.32 ms — **Index Only Scan** trên `idx_order_items_seller_id` (không cần đọc heap) | **~42×** |

Ghi chú quan trọng rút ra khi đo: với query có `LIMIT` nhỏ và điều kiện filter không chọn lọc cao, Seq Scan đôi khi "may mắn" gặp đủ 20 dòng khớp ngay từ đầu bảng và dừng sớm — khiến execution time đo được có vẻ nhanh dù kế hoạch tổng thể (không giới hạn) chậm hơn nhiều. Vì vậy khi benchmark, đo thêm biến thể không `LIMIT` (hoặc `COUNT(*)`) để thấy đúng chi phí thật, không chỉ dựa vào query có `LIMIT` — đây chính là lý do Q4 phải đo lại 2 lần (có và không `LIMIT`) để tránh kết luận sai.

## 3. Search/filter nâng cao (Product module)

`GET /api/products` hỗ trợ dynamic filter: `category`, `seller`, `minPrice`, `maxPrice`, `keyword`, `minRating`, `inStockOnly`, pagination (`page`, `size`), sort (giá, rating, mới nhất).

Triển khai bằng **JPA Specification** (dynamic query, tránh viết N query riêng cho từng tổ hợp filter). Đây là kỹ năng match trực tiếp với yêu cầu "viết truy vấn phức tạp, tối ưu performance" trong JD.

## 4. Response & error handling convention

- Response envelope thống nhất: `{ "success": bool, "data": ..., "error": null | { code, message } }`
- Global exception handler (`@RestControllerAdvice`) map exception → HTTP status chuẩn (400 validation, 401/403 auth, 404 not found, 409 conflict — ví dụ đặt hàng khi hết hàng, 500 unexpected).
- `@RequestParam` sai kiểu (VD `from=not-a-date`, `minPrice=abc`) → `MethodArgumentTypeMismatchException` được bắt riêng, trả `400 INVALID_PARAMETER` thay vì rơi vào handler `Exception` chung (500) — phát hiện khi tự rà soát report endpoints (Phase 6), áp dụng chung cho toàn bộ app.
- DTO tách biệt hoàn toàn khỏi Entity — không bao giờ trả Entity trực tiếp ra API.

## 5. Roadmap

### v1 — phase triển khai đề xuất

1. **Nền tảng**: User + Auth (JWT, refresh token), Seller identity, Category, Product CRUD cơ bản, Flyway migration, Swagger.
2. **Business logic cốt lõi**: Cart, Order + Inventory (reserve/release transaction, optimistic + conditional-update locking), payment timeout worker (15 phút).
3. **Payment**: tích hợp VNPay sandbox (redirect payment, IPN, idempotency), refund flow.
4. **Fulfillment**: Seller pack order-item (aggregate rule), Shipping module (assign shipper, delivery status, retry), đồng bộ ngược order status, scheduled job auto-complete `DELIVERED → COMPLETED` sau 3 ngày.
5. **Search & filter nâng cao**: JPA Specification, pagination, index tuning + đo bằng `EXPLAIN ANALYZE`.
6. **Reporting**: aggregation query (doanh thu theo ngày/category, top sản phẩm), Admin endpoints.
7. **Production-readiness**: unit + integration test (Testcontainers), Actuator (health/metrics), structured logging, Dockerize, README mô tả kiến trúc + cách chạy.

Phase 7 quan trọng không kém các phase trước — đây là phần giúp có câu chuyện trả lời câu hỏi behavioral về "theo dõi hệ thống, xử lý sự cố" dù chỉ ở quy mô project cá nhân.

### v2 — ngoài phạm vi v1 (đã quyết định hoãn, có lý do cụ thể — xem mục 0.5/0.6)

- Coupon / Promotion engine (giảm giá, điều kiện áp dụng, giới hạn lượt dùng có concurrency) — **đã thiết kế chi tiết ở mục 6, đã triển khai**.
- Return / Exchange flow (state machine riêng, tương tác lại với refund) — **đã thiết kế chi tiết ở mục 7**.
- Notification service (email khi order đổi trạng thái) — **đã thiết kế chi tiết ở mục 8**.
- Seller payout / tính hoa hồng — **đã thiết kế chi tiết ở mục 9**.
- Tách shipment theo từng seller trong 1 order (thay vì 1 shipment/order) — **phác thảo sơ bộ ở mục 10, thiết kế chi tiết dời tới sát lúc code** (thay đổi cấu trúc lớn nhất, rủi ro cao nhất, nên làm sau cùng).
- Multi-currency — **phác thảo sơ bộ ở mục 11**, giá trị thấp cho project cá nhân, cân nhắc bỏ qua (xem mục 11).

Thứ tự triển khai đề xuất: 7 (Return/Exchange) → 8 (Notification) → 9 (Seller payout) → 10 (Shipment split) → 11 (Multi-currency, tùy chọn) — ưu tiên theo độ độc lập với module khác và mức độ tái dùng pattern đã có, tương tự lý do đã chọn Coupon làm trước ở mục 6.

## 6. v2 — Module Coupon/Promotion (Phase 8)

Bản nháp thiết kế đầu tiên của v2, chọn làm trước vì độc lập với các module khác và bài toán
concurrency cốt lõi (giới hạn lượt dùng) tái dùng gần như nguyên xi pattern `available/reserved`
đã kiểm chứng ở module Inventory (mục 0.9) — củng cố kỹ năng đã có thay vì mở bài toán mới.

### 6.1 Phạm vi v2 (MVP cho coupon — chừa lại việc mở rộng cho v3)

- **Loại discount**: `PERCENTAGE` (có `max_discount_amount` tùy chọn để chặn trần) hoặc `FIXED_AMOUNT`.
- **Điều kiện áp dụng**: chỉ `min_order_amount` (đơn tối thiểu). **Không** áp dụng theo category/seller/product cụ thể ở v2 — lý do: nếu giới hạn theo category, discount phải prorate qua từng `order_item`, gặp lại đúng vấn đề "refund không breakdown được theo item" đã ghi nhận là giới hạn của Report module (mục 0.9) — tránh nhân đôi vấn đề cũ trong tính năng mới.
- **1 order = tối đa 1 coupon** — không stacking. Stacking (nhiều coupon cộng dồn, thứ tự áp dụng, ưu tiên) là bài toán độc lập, để v3 nếu cần.
- **Giới hạn lượt dùng**: `usage_limit` toàn hệ thống (nullable = không giới hạn) + **mỗi user tối đa 1 lần/coupon** (hardcode, không cấu hình N lần/user ở v2 — xem lý do concurrency ở mục 6.4).
- **Vòng đời coupon**: admin tạo/sửa, `status` (`ACTIVE`/`INACTIVE`, giống Product — soft toggle, không xóa cứng) + cửa sổ thời gian `starts_at`/`ends_at` (nullable = không giới hạn).

### 6.2 User Flows & Edge Cases

- Coupon hết hạn/`INACTIVE`/chưa tới `starts_at`/đã quá `ends_at` lúc checkout → từ chối, **không tạo order** (giữ nguyên nguyên tắc "checkout thất bại giữa chừng → rollback toàn bộ", không âm thầm bỏ qua coupon rồi tạo order full giá).
- Đơn không đạt `min_order_amount` → từ chối tương tự, không tự động điều chỉnh.
- 2 request checkout cùng lúc dùng nốt lượt cuối của 1 coupon giới hạn → conditional UPDATE (mục 6.4) đảm bảo chỉ 1 request thành công, request thua nhận lỗi rõ ràng để thử lại (không dùng coupon hoặc coupon khác) — đúng pattern race-condition đã xử lý cho tồn kho.
- User double-submit cùng 1 coupon (2 tab/device) → chặn bằng unique constraint DB (mục 6.4), không chặn bằng kiểm tra đọc-trước-ghi (dễ race).
- Discount tính ra lớn hơn tổng đơn (VD `FIXED_AMOUNT` 100k cho đơn 80k) → discount bị clamp về đúng bằng tổng đơn, `total_amount` sau discount tối thiểu là 0, không âm.
- Preview discount trước khi bấm đặt hàng (FE muốn hiển thị "tiết kiệm được X") → endpoint riêng **không** giữ chỗ lượt dùng (xem mục 6.5) — nếu dùng chung cơ chế reserve của checkout, mỗi lần user gõ thử mã sẽ ngốn 1 lượt dùng thật dù chưa đặt hàng.
- Order dùng coupon bị hủy **trước khi thanh toán** (`PENDING_PAYMENT → CANCELLED`, hoặc `PAYMENT_EXPIRED`) → trả lại lượt dùng cho coupon (và cho user), y hệt nguyên tắc release tồn kho.
- Order dùng coupon **đã thanh toán** rồi mới hủy/refund → lượt dùng coi như đã tiêu, **không hoàn lại** — nhất quán với nguyên tắc "cancel sau payment: available không tự tăng lại" (mục 0.9), coupon không có khái niệm "nhập lại" giống hàng tồn kho.
- Admin sửa `usage_limit` (hạ xuống) trong lúc đang có request reserve chạy đồng thời → bảo vệ bằng `@Version` optimistic lock trên chính hàng `coupons` khi admin ghi đè — tách biệt với conditional UPDATE ở hot path (đúng 2-cơ-chế-song-song đã áp dụng cho Inventory: `WHERE` clause bảo vệ hot path, `@Version` bảo vệ đường ghi đè thủ công).

### 6.3 Business Rules chốt

**Thời điểm validate + giữ chỗ coupon: tại CHECKOUT, không phải lúc thêm giỏ hàng** — nhất quán với nguyên tắc "giá đóng băng lúc checkout" (Cart không snapshot giá, Order mới snapshot). `discount_amount` được tính và ghi cố định vào `orders` ngay khi tạo order, không tính lại sau đó.

**Coupon dùng cơ chế reserve/commit/release y hệt Inventory** (mục 0.9), áp trên cặp cột
`usage_reserved`/`usage_committed` thay vì `available`/`reserved`:

| Event | usage_reserved | usage_committed |
|---|---|---|
| Checkout (giữ chỗ coupon) → `PENDING_PAYMENT` | ↑ | — |
| Payment success → `CONFIRMED` | ↓ | ↑ (đã tính là dùng thật) |
| `PAYMENT_EXPIRED` / Cancel trước payment | ↓ (trả lại) | — |
| Cancel sau payment (`CONFIRMED`/`PACKED` trở đi) | — | Không giảm — đã tiêu |

Slot còn trống = `usage_limit IS NULL OR usage_reserved + usage_committed < usage_limit`.

**`orders` sở hữu trực tiếp kết quả áp dụng coupon** (giống cách `orders` đã tự giữ
`shipping_recipient_name`/`total_amount` — không tách sang module riêng): thêm `coupon_code`
(snapshot, không FK) + `discount_amount`. Lý do không FK: cùng nguyên tắc `order_items.product_id`
— Order cần giữ lịch sử đúng những gì khách đã thấy lúc đặt hàng, kể cả khi coupon sau đó bị admin
sửa/xóa. Chi tiết đầy đủ (ai dùng, lúc nào, trạng thái reserve/commit) thuộc về bảng
`coupon_redemptions` của module Coupon, `orders` chỉ giữ đúng phần cần cho hiển thị/tính tiền.

**`payments.amount` không đổi cách tính** — vẫn lấy nguyên `orders.total_amount` (đã là giá sau
discount, tính 1 lần lúc checkout). Payment module không cần biết khái niệm coupon tồn tại.

**Giao tiếp module**: `OrderService.checkout()` gọi `CouponService.reserve(user, code, cartTotal)`
qua service interface (không query thẳng repository của Coupon), nằm trong cùng transaction hiện
có của checkout (thuần DB, không có network call nên không vi phạm nguyên tắc "không giữ transaction
khi gọi ngoài" đã áp dụng cho VNPay). Giữ nhất quán gọi trực tiếp qua interface như toàn bộ hệ thống
— **không** dùng `ApplicationEventPublisher` cho riêng module này dù có thể, vì trộn 2 phong cách
giao tiếp (gọi trực tiếp cho phần lớn hệ thống, event riêng cho 1 module) làm code khó đoán hơn là
lợi ích mang lại ở quy mô hiện tại.

### 6.4 Concurrency — vì sao unique constraint thay vì đếm-rồi-chèn

Giới hạn "mỗi user 1 lần/coupon" **không** dùng pattern SELECT COUNT rồi INSERT nếu đạt — 2 request
đồng thời của cùng 1 user có thể cùng đọc count=0 trước khi request nào kịp INSERT (race giống hệt
lớp học ở mục IPN idempotency, mục 0.6: "check + lưu phải cùng 1 bước atomic"). Giải pháp: partial
unique index chặn ở tầng DB:

```sql
CREATE UNIQUE INDEX uq_coupon_redemptions_active
    ON coupon_redemptions (coupon_id, user_id)
    WHERE status IN ('RESERVED', 'COMMITTED');
```

`CouponServiceImpl.reserve()` cứ `INSERT` thẳng, bắt `DataIntegrityViolationException` (vi phạm
unique) → ném `CouponAlreadyUsedException` — không có bước kiểm tra riêng trước INSERT. Đây là kỹ
thuật "constraint-first, catch-exception" đã dùng cho `payment_webhook_events` (mục 0.6), áp dụng
lại ở đây cho đúng loại race condition tương tự.

Giới hạn global `usage_limit` dùng conditional `UPDATE ... WHERE usage_reserved + usage_committed <
usage_limit OR usage_limit IS NULL` — đúng kỹ thuật đã dùng cho `inventory.quantity_available`.

### 6.5 API Endpoint (đề xuất)

```
COUPON (customer)
POST   /api/coupons/validate      (body: code, cartTotal — CHỈ kiểm tra + tính discount preview,
                                    KHÔNG reserve, dùng cho FE hiển thị trước khi checkout)

ORDER (mở rộng CheckoutRequest hiện có)
POST   /api/orders/checkout       (thêm field tùy chọn couponCode — reserve thật sự nằm trong
                                    transaction checkout, không phải endpoint riêng)

ADMIN
POST   /api/admin/coupons
PATCH  /api/admin/coupons/{id}
PATCH  /api/admin/coupons/{id}/status         (ACTIVE/INACTIVE — soft toggle, không DELETE)
GET    /api/admin/coupons                     (pagination ngay từ đầu — bài học từ v1 audit,
                                                không để list endpoint thiếu Pageable rồi vá sau)
GET    /api/admin/coupons/{id}/redemptions    (lịch sử dùng, pagination)
```

### 6.6 Schema

```
coupons
- id (PK), code (unique), discount_type (enum: PERCENTAGE, FIXED_AMOUNT)
- discount_value (numeric), max_discount_amount (nullable — chỉ có ý nghĩa với PERCENTAGE)
- min_order_amount (numeric, default 0)
- usage_limit (nullable int — null = không giới hạn)
- usage_reserved (int, default 0), usage_committed (int, default 0)
- status (enum: ACTIVE, INACTIVE)
- starts_at (nullable), ends_at (nullable)
- version (optimistic lock — @Version, bảo vệ đường admin sửa tay, xem mục 6.2)
- created_at, updated_at

coupon_redemptions
- id (PK), coupon_id (FK → coupons.id — cùng module, giống Refund → Payment)
- order_id (reference, không FK — Order là module giao dịch cốt lõi, cùng quy tắc với
  payments.order_id/refunds.order_id/deliveries.order_id)
- user_id (FK → users.id — module nền, giống Review → User)
- discount_amount_snapshot (numeric — số tiền giảm thực tế, để đối soát độc lập với orders.discount_amount)
- status (enum: RESERVED, COMMITTED, RELEASED)
- created_at, updated_at

UNIQUE (coupon_id, user_id) WHERE status IN ('RESERVED', 'COMMITTED')   -- xem mục 6.4

orders (thêm cột, không tạo bảng mới)
- coupon_code (nullable, snapshot — không FK)
- discount_amount (numeric, default 0)
```

Index gợi ý: `coupons(code)` unique (đã có qua constraint), `coupon_redemptions(order_id)` cho
đường release/commit tra theo order, `coupon_redemptions(coupon_id, user_id)` (đã có qua unique
index ở trên, đủ dùng cho tra cứu per-user).

### 6.7 Điểm cần bạn quyết định lại nếu không đồng ý

- Đã giả định **percentage discount có cap tối đa tùy chọn** — nếu muốn bắt buộc luôn phải có cap
  cho `PERCENTAGE` (tránh admin lỡ tạo coupon giảm 100% không giới hạn số tiền), cần đổi
  `max_discount_amount` từ nullable sang bắt buộc khi `discount_type = PERCENTAGE`.
- Đã giả định **preview không cần auth** (khách chưa đăng nhập vẫn xem được discount) — nếu coupon
  chỉ dành cho user đã đăng nhập (VD coupon sinh nhật cá nhân hóa), `/api/coupons/validate` cần
  `@AuthenticationPrincipal` bắt buộc, không phải optional.
- Đã chọn **1 coupon/order, không stacking** — nếu có nhu cầu thật (VD coupon giảm giá + coupon
  freeship cộng dồn), đây là thiết kế lại từ đầu (thứ tự áp dụng, tổng discount tối đa), không phải
  mở rộng nhỏ từ thiết kế hiện tại.

## 7. v2 — Module Return/Exchange (Phase 9)

### 7.1 Phạm vi v2 (thu hẹp: chỉ Return, Exchange dời sang v3)

**Quyết định thu hẹp scope trước khi thiết kế**: tên gốc trong roadmap là "Return/Exchange" nhưng
Exchange (đổi sang sản phẩm/variant khác thay vì hoàn tiền) kéo theo một luồng riêng gần như tạo
lại 1 order mới (chọn sản phẩm thay thế, kiểm tra tồn kho sản phẩm mới, chênh lệch giá phải thu
thêm/hoàn lại) — độ phức tạp tương đương module Order thu nhỏ, không phải mở rộng nhỏ từ Return.
Theo đúng nguyên tắc đã áp dụng cho Coupon (mục 6.1: thu hẹp scope để tránh nhân đôi vấn đề cũ),
**v2 chỉ build Return (hoàn tiền), Exchange ghi nhận lùi tiếp sang v3**.

- **Đơn vị return**: theo từng `order_item` (không phải toàn order) — 1 order nhiều sản phẩm, khách
  có thể chỉ trả 1 món. Vẫn tương thích với model "1 shipment/order" của v1 vì return là 1 luồng
  hoàn toàn mới (khách tự gửi trả qua đơn vị vận chuyển riêng, không phải chia nhỏ shipment giao
  hàng gốc) — không đụng tới giả định "chưa tách shipment" đã chốt ở mục 0.
- **Điều kiện được return**: order đã ở trạng thái `DELIVERED` **hoặc** `COMPLETED` — cố ý dùng
  đúng 2 trạng thái này, nhất quán với Review eligibility đã chốt (mục 0.6: "cho phép review khi
  order_item... DELIVERED", suy ra từ `orders.status = DELIVERED/COMPLETED`). Bắt khách chờ tới khi
  `COMPLETED` (tự động sau 3 ngày) mới được gửi yêu cầu return là ma sát không cần thiết và không có
  lý do nghiệp vụ để khắt khe hơn Review. Trong vòng `RETURN_WINDOW_DAYS` (mặc định 7 ngày) kể từ
  thời điểm `orders.status` chuyển sang `DELIVERED` (lấy từ `order_status_history`, cùng kỹ thuật đã
  dùng cho Report — mục 0.9 — tránh phụ thuộc `updated_at` có thể bị ghi đè bởi transition khác).
- **Reason code**: enum cố định (`DEFECTIVE`, `WRONG_ITEM`, `NOT_AS_DESCRIBED`, `CHANGED_MIND`,
  `OTHER`) + ghi chú tự do — không phân loại chính sách hoàn tiền khác nhau theo reason ở v2 (VD
  "lỗi từ shop thì không trừ phí ship" là bài toán riêng, ghi nhận cho v3).
- **Duyệt thủ công**: seller/admin duyệt (`APPROVED`/`REJECTED`) — không tự động duyệt theo rule,
  vì đánh giá "sản phẩm có thực sự lỗi" cần con người, không phải business rule máy tính được.
- **Không có exchange, không có refund một phần theo % tùy ý** — return 1 item luôn hoàn đúng
  `unit_price_snapshot * quantity` của item đó (không hỗ trợ trả 1 phần số lượng trong 1 dòng
  order_item ở v2 — trả hết số lượng của dòng đó hoặc không trả).

### 7.2 User Flows & Edge Cases

- Khách gửi yêu cầu return khi order chưa tới `DELIVERED` (VD còn `SHIPPED`) → từ chối, thuộc case
  "chưa nhận hàng thật sự". **Lưu ý**: đây **không** phải lúc dùng Cancel flow thay thế — Cancel
  policy (mục 0.6) đã chốt rõ từ `PACKED` trở đi khách không còn tự hủy được nữa, nên ở khoảng
  `PACKED`–`SHIPPED` khách **không có hành động nào khả dụng**, chỉ có thể chờ hàng tới hoặc chờ admin
  can thiệp qua kênh hỗ trợ thông thường — không phải giới hạn riêng của module Return.
- Khách gửi yêu cầu sau khi hết `RETURN_WINDOW_DAYS` → từ chối, không có ngoại lệ tự động (muốn
  ngoại lệ thì admin tạo return request thay khách — action riêng, xem mục 7.5).
- Khách gửi 2 yêu cầu return cho cùng 1 `order_item` (VD request đầu bị `REJECTED`, muốn gửi lại) →
  cho phép, miễn không có request nào đang ở trạng thái "đang xử lý" (`REQUESTED`/`APPROVED`) cho
  chính `order_item` đó — chặn bằng unique constraint có điều kiện, cùng kỹ thuật mục 6.4.
- Seller duyệt (`APPROVED`) nhưng khách không gửi hàng trả lại → cần cơ chế "auto-expire" giống
  payment timeout: `APPROVED` quá `RETURN_SHIP_BACK_DAYS` (mặc định 7 ngày) không chuyển
  `ITEM_RECEIVED` → tự động `EXPIRED` (không hoàn tiền), do scheduled job (cùng họ với
  `OrderMaintenanceProcessor`).
- Seller/admin xác nhận đã nhận lại hàng (`ITEM_RECEIVED`) nhưng hàng thực tế không đúng/hư hỏng
  thêm do khách → v2 không có bước "kiểm tra chất lượng hàng trả" riêng; `ITEM_RECEIVED` ngụ ý đã
  chấp nhận, chuyển thẳng sang hoàn tiền. Case tranh chấp phức tạp hơn (hàng trả không đúng) cần can
  thiệp thủ công qua kênh admin thông thường, không có state riêng cho nó ở v2.
- Return được duyệt và hoàn tiền, nhưng seller đã được payout cho order đó (module 9) → **buộc phải
  đảo ngược ledger entry của seller** — xem tương tác chi tiết ở mục 9.4. Đây là điểm nối quan trọng
  nhất giữa 2 module v2 mới, phải cài đặt Return trước hoặc cùng lúc với Payout để tránh nợ kỹ thuật.
- Khách hủy yêu cầu return giữa chừng (đổi ý, không muốn trả nữa) trước khi seller duyệt → cho phép
  tự hủy khi còn `REQUESTED`; từ `APPROVED` trở đi không tự hủy được nữa (đã tốn công seller xử lý,
  nhất quán với nguyên tắc cancel-theo-trạng-thái của Order ở mục 0.6).
- Refund thất bại ở bước cuối (gọi `PaymentService.refund()`) → tái dùng đúng cơ chế `REFUND_FAILED`
  đã có (mục 0.6/0.9): `return_requests.status` dừng ở `REFUND_FAILED`, không tự lùi về trạng thái
  trước, cần admin can thiệp qua `POST /api/admin/returns/{id}/refund/retry` (đối xứng với order
  refund retry đã có).

### 7.3 Business Rules chốt

**State machine `return_requests.status` (module `return`, độc lập, không đè lên `orders.status`)**:

```
REQUESTED
 ├── REJECTED                        (terminal — seller/admin từ chối)
 ├── CANCELLED                       (khách tự hủy, chỉ khi còn REQUESTED)
 └── APPROVED
       ├── EXPIRED                   (terminal — quá hạn gửi trả, không hoàn tiền)
       └── ITEM_RECEIVED → REFUND_PENDING → REFUNDED / REFUND_FAILED
```

Đúng nguyên tắc đã chốt cho refund cấp order (mục 0.6): **không** nhét thêm trạng thái return vào
`orders.status` — order vẫn giữ nguyên `COMPLETED`. Toàn bộ vòng đời return sống trong bảng
`return_requests` riêng, giống cách `refunds` độc lập với `orders`.

**Mọi transition ghi vào `return_status_history`** — cùng nguyên tắc bắt buộc áp dụng cho
`order_status_history`/`delivery_status_history` (mục 0.6).

**Restock**: khi `ITEM_RECEIVED`, gọi `InventoryService.restock(productId, quantity)` — hành động
**mới**, không tái dùng `releaseStock()` hiện có của checkout, vì bản chất khác nhau: `release`
là "trả lại chỗ đã giữ nhưng chưa từng giao" (cộng `available`, trừ `reserved` — reserved đã về 0
từ lâu ở case này vì order đã `CONFIRMED`), còn restock ở đây là "hàng vật lý quay lại kho sau khi
đã bán" (chỉ cộng thẳng `available`, không đụng `reserved`). Tên hàm khác nhau để tránh nhầm 2 luồng
nghiệp vụ khác bản chất dù cùng chạm 1 bảng.

**Refund**: `ReturnService` gọi `PaymentService.refund(paymentId, amount, reason)` qua service
interface — tái dùng nguyên bảng `refunds` và state machine `REFUND_PENDING/REFUNDED/REFUND_FAILED`
đã có ở module Payment (mục 0.9), không tạo enum refund riêng cho return. `refunds` cần thêm cột
tham chiếu ngược `return_request_id` (nullable, không FK — cùng quy tắc reference-only giữa các
module giao dịch cốt lõi) để phân biệt refund do return với refund do cancel order thông thường.

**Transaction boundary khi gọi refund thật (bắt buộc, không phải tùy chọn)**: bước
`ITEM_RECEIVED → REFUND_PENDING` chỉ là ghi DB thuần (an toàn nằm trong transaction chính), nhưng
bước gọi `PaymentService.refund()` thật sự (network call tới VNPay Refund API) **phải** chạy ngoài
transaction đang giữ lock của `return_requests`/`inventory` — đúng nguyên tắc "không giữ transaction
khi gọi ngoài" đã áp dụng cho toàn bộ luồng VNPay hiện có (`REQUIRES_NEW` cho
`PaymentResultApplier`/refund ledger, xem mục 0.6). Đây là loại lỗi **đã từng xảy ra thật** trong
chính project này (đã sửa ở commit `daf5c49` — "payment tx-scoped HTTP call") — ghi chú tường minh ở
đây để không tái phạm khi code module Return.

**Prorate discount khi tính refund (bắt buộc để không vi phạm invariant refund cấp payment)**:
`refund_amount_snapshot` **không** được lấy nguyên `unit_price_snapshot * quantity` nếu order có
áp coupon (mục 6) — vì `payments.amount` là số tiền **sau** discount, còn `unit_price_snapshot` là
giá **trước** discount. Nếu hoàn nguyên giá gốc cho từng item trả riêng lẻ, tổng `refunds.amount`
của 1 payment hoàn toàn có thể vượt `payments.amount` — vi phạm thẳng rule đã chốt ở mục 0.6
("Tổng refunds.amount... không được vượt quá payments.amount"). Công thức prorate:

```
discount_ratio = orders.total_amount / (orders.total_amount + orders.discount_amount)
                  -- = 1 nếu order không dùng coupon (discount_amount = 0)
refund_amount_snapshot = ROUND(unit_price_snapshot * quantity * discount_ratio, 2)
```

Tính 1 lần tại thời điểm tạo `return_requests` (đọc `orders.total_amount`/`discount_amount` hiện
tại của order đó — 2 giá trị này không đổi sau khi order đã tạo, xem mục 6.3) và lưu cố định vào
`refund_amount_snapshot`, không tính lại sau đó. `ReturnService.approve()`/luồng tạo refund vẫn nên
validate phòng thủ thêm: tổng `refund_amount_snapshot` đã `REFUNDED`/`REFUND_PENDING` của cùng 1
order không được vượt `payments.amount - đã refund trước đó do cancel` — chặn cứng ở tầng
`PaymentService.refund()` (nơi đã sở hữu invariant này), không phải chỉ tin vào công thức prorate.

### 7.4 Module boundary & FK convention

`return` là module giao dịch cốt lõi mới (biến động cao, tương tác Order/Payment/Inventory) — áp
dụng đúng quy tắc đã chốt ở mục 0.8: tham chiếu tới Order/Payment bằng reference thường (không FK),
tham chiếu tới User bằng FK thật (module nền).

```
return_requests
- order_id (reference, không FK), order_item_id (reference, không FK)
- user_id (FK → users.id)
```

### 7.5 API Endpoints (đề xuất)

```
RETURN (customer)
POST   /api/returns                          (body: orderItemId, reason, note)
GET    /api/returns                           (lịch sử return của chính mình, pagination)
POST   /api/returns/{id}/cancel               (chỉ khi còn REQUESTED)

SELLER
GET    /api/seller/returns                    (return request cho order_item thuộc seller mình)
PATCH  /api/seller/returns/{id}/approve
PATCH  /api/seller/returns/{id}/reject
PATCH  /api/seller/returns/{id}/item-received

ADMIN
GET    /api/admin/returns                     (toàn hệ thống, pagination, filter theo status)
POST   /api/admin/returns/{id}/force-approve  (can thiệp khi seller không xử lý)
POST   /api/admin/returns/{id}/refund/retry
```

### 7.6 Schema

```
return_requests
- id (PK), order_id (reference, không FK), order_item_id (reference, không FK)
- user_id (FK → users.id), seller_id (reference, không FK — snapshot từ order_item lúc tạo request)
- reason (enum: DEFECTIVE, WRONG_ITEM, NOT_AS_DESCRIBED, CHANGED_MIND, OTHER), note (nullable)
- refund_amount_snapshot (numeric — unit_price_snapshot * quantity đã prorate theo discount_ratio
  của order tại thời điểm tạo, xem công thức mục 7.3 — KHÔNG phải giá gốc chưa trừ discount)
- status (enum: REQUESTED, APPROVED, REJECTED, CANCELLED, EXPIRED, ITEM_RECEIVED,
           REFUND_PENDING, REFUNDED, REFUND_FAILED)
- approved_at, item_received_at, expires_at (nullable — set khi APPROVED, dùng cho auto-expire)
- created_at, updated_at

return_status_history
- id (PK), return_request_id (FK → return_requests), from_status, to_status
- changed_by (FK → users, nullable — null nếu hệ thống tự động, VD auto-expire)
- reason (nullable)
- created_at

UNIQUE (order_item_id) WHERE status IN
    ('REQUESTED', 'APPROVED', 'ITEM_RECEIVED', 'REFUND_PENDING', 'REFUND_FAILED')
    -- mọi trạng thái CHƯA terminal (kể cả REFUND_FAILED — vẫn "đang xử lý", chờ admin retry, xem
    -- mục 7.2) đều phải nằm trong danh sách chặn; chỉ REJECTED/CANCELLED/EXPIRED/REFUNDED (terminal
    -- thật sự) mới cho phép tạo request mới cho cùng order_item — cùng kỹ thuật mục 6.4

refunds (thêm cột, không tạo bảng mới)
- return_request_id (nullable, reference, không FK — phân biệt refund do return vs do cancel order)
```

### 7.7 Điểm cần bạn quyết định lại nếu không đồng ý

- Đã giả định **thu hẹp scope, bỏ Exchange sang v3** — nếu Exchange là phần bắt buộc phải có (VD
  muốn kể câu chuyện phỏng vấn về bài toán này), cần thiết kế lại từ đầu, không phải mở rộng nhỏ.
- Đã giả định **return theo từng item, hoàn nguyên số lượng của dòng** (không hỗ trợ trả 1 phần số
  lượng trong 1 order_item) — nếu cần hỗ trợ "mua 3 trả 1", cần thêm cột `quantity` vào
  `return_requests` thay vì suy ra nguyên dòng.
- Đã giả định **return window cố định 7 ngày, hardcode** (giống usage-limit 1 lần/user của Coupon)
  — nếu cần cấu hình theo category/seller, đây là mở rộng schema (thêm cột ở `products` hoặc
  `sellers`), không phải business logic riêng của module Return.
- Đã giả định **prorate refund theo tỷ lệ discount toàn order** (mục 7.3) khi order có coupon — nếu
  coupon chỉ nên áp cho 1 số sản phẩm cụ thể (không phải toàn order) thì công thức này sai; nhưng vì
  Coupon v2 chỉ có điều kiện `min_order_amount` (không giới hạn theo sản phẩm/category — mục 6.1),
  prorate đều theo tỷ lệ giá trị là cách hợp lý duy nhất tương thích với thiết kế coupon hiện tại.

## 8. v2 — Module Notification (Phase 10)

### 8.1 Phạm vi v2 (chỉ email, chỉ theo trạng thái đơn hàng — không push, không SMS)

- **Kênh duy nhất**: email. Push notification/SMS cần thêm hạ tầng (FCM, SMS gateway) không phục vụ
  mục tiêu luyện backend cốt lõi của project — ghi nhận cho v3 nếu cần.
- **Sự kiện kích hoạt (v2)**: `orders.status` chuyển sang `CONFIRMED`, `SHIPPED`, `DELIVERED`,
  `CANCELLED`; `refunds.status` chuyển `REFUNDED`. Không bao gồm mọi transition nhỏ (VD
  `PACKED` không cần email) — chỉ những mốc khách hàng thực sự quan tâm.
- **Không có in-app notification list** ở v2 (không cần bảng đọc/chưa đọc kiểu "thông báo trong
  app") — chỉ gửi email, endpoint duy nhất phía user là xem "email đã gửi cho tôi chưa" gián tiếp
  qua chính hộp thư, không qua API.
- **Không đảm bảo delivery thật (không tích hợp SMTP thật ở giai đoạn dev)** — dùng
  Mailhog/Mailtrap (SMTP giả lập) để có thể demo đầy đủ luồng mà không cần domain email thật; kiến
  trúc gửi/retry vẫn giống hệt production, chỉ khác nơi email "hạ cánh".

### 8.2 User Flows & Edge Cases

- Order đổi trạng thái nhưng gửi email thất bại (SMTP timeout, provider lỗi) → **không được làm
  transaction nghiệp vụ chính (đổi order status) rollback theo** — đây là bài học đã áp dụng cho VNPay
  (mục "REQUIRES_NEW... network calls must never run inside the same transaction as row locks", xem
  Key Technical Concepts) và lặp lại y hệt ở đây: ghi nhận thất bại, cho retry sau, không chặn luồng
  chính.
- Cùng 1 order đổi trạng thái 2 lần liên tiếp rất nhanh (VD do admin sửa tay rồi sửa lại) → mỗi
  transition tạo 1 dòng `notifications` riêng, **không** gộp/dedupe — chấp nhận khách nhận 2 email,
  đơn giản hơn cơ chế gộp thông minh mà lợi ích không tương xứng ở quy mô project.
- Gửi email thất bại (SMTP timeout, provider tạm thời từ chối...) → `NotificationDispatcher` tăng
  `attempt_count`, tính lại `next_retry_at` (exponential backoff), cần retry đến khi đạt
  `MAX_ATTEMPTS` (mặc định 5) rồi dừng, đánh dấu `FAILED` vĩnh viễn — không retry vô hạn (tránh lấp
  đầy queue bởi 1 địa chỉ email luôn bounce).
- Email gửi thành công nhưng job crash trước khi ghi lại `status = SENT` → worker chạy lại đọc thấy
  dòng vẫn `PENDING`, gửi lại → **duplicate email possible, chấp nhận đánh đổi** (tương tự nguyên
  tắc "at-least-once" IPN, khác ở chỗ hậu quả ở đây chỉ là khách nhận email trùng, không phải tiền —
  mức độ nghiêm trọng thấp hơn nhiều, không cần cơ chế exactly-once tốn kém).
- User đổi email sau khi order đã tạo → gửi tới email tại **thời điểm gửi** (đọc `users.email` hiện
  tại qua `UserService`, không snapshot email lúc tạo order) — khác với coupon/order vốn snapshot
  giá/thông tin giao hàng, vì thông báo luôn nên tới đúng địa chỉ liên hệ hiện tại của khách, không
  phải "đóng băng lịch sử".

### 8.3 Business Rules chốt — kiến trúc outbox + scheduled worker

**Không gửi email đồng bộ ngay trong request** (VD gọi thẳng `JavaMailSender.send()` bên trong
`OrderServiceImpl.confirmPayment()`) — vì (a) email chậm/không ổn định, làm chậm response chính, và
(b) lỗi gửi email không được phép làm rollback nghiệp vụ chính đã hoàn tất. Thay vào đó dùng
**outbox pattern**, tái dùng đúng tinh thần scheduled-worker đã kiểm chứng ở Payment
timeout/`OrderMaintenanceProcessor`:

1. Module Order/Payment publish domain event nội bộ qua `ApplicationEventPublisher` (đã được cho
   phép ở mục 0.8 cho side-effect nội bộ) — VD `OrderConfirmedEvent(orderId)`.
2. `NotificationListener` lắng nghe qua `@TransactionalEventListener(phase = BEFORE_COMMIT)`, ghi
   1 dòng vào bảng `notifications` (status `PENDING`) — **cùng transaction** với thay đổi trạng thái
   order, đảm bảo không bao giờ "đổi status thành công nhưng quên ghi outbox" (khác với gửi email
   trực tiếp — outbox insert là thao tác DB thuần, không phải network call, nên an toàn nằm chung
   transaction, đúng nguyên tắc "chỉ tách transaction khi có network call thật").
3. `NotificationDispatcher` (scheduled job, cùng họ với `OrderMaintenanceProcessor`) định kỳ quét
   `notifications WHERE status = 'PENDING' AND next_retry_at <= now()`, gửi qua `EmailSender`
   (interface bọc `JavaMailSender`), cập nhật `SENT`/tăng `attempt_count` + tính lại `next_retry_at`
   (exponential backoff) nếu thất bại, `FAILED` khi vượt `MAX_ATTEMPTS`.
4. Việc gửi email thật (bước 3) chạy trong transaction `REQUIRES_NEW` riêng cho từng dòng, giống hệt
   pattern đã dùng cho refund/payment-result-applier (mục Key Technical Concepts) — 1 email lỗi
   không kéo rollback cả batch.

**Giao tiếp module**: Order/Payment module không phụ thuộc trực tiếp vào Notification module (không
import `NotificationService` vào `OrderServiceImpl`) — chỉ publish event nội bộ, Notification module
tự lắng nghe. Đây là ngoại lệ hợp lý duy nhất khác Report được phép "không gọi qua service interface
trực tiếp", vì bản chất là thông báo side-effect, không phải đọc/ghi dữ liệu nghiệp vụ — giữ đúng
tinh thần "module khác không biết Notification tồn tại" (loose coupling tối đa cho 1 module thuần
side-effect).

### 8.4 API Endpoints (đề xuất — tối thiểu)

```
ADMIN (chỉ để vận hành/debug, không có API phía customer ở v2)
GET    /api/admin/notifications                (pagination, filter theo status — xem cái nào FAILED)
POST   /api/admin/notifications/{id}/retry     (reset attempt_count, next_retry_at = now, cho job
                                                 nhặt lại ở lượt quét kế tiếp)
```

### 8.5 Schema

```
notifications
- id (PK), user_id (FK → users.id)
- type (enum: ORDER_CONFIRMED, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED, ORDER_REFUNDED)
- channel (enum: EMAIL — chừa chỗ mở rộng kênh khác ở v3, không xử lý logic khác kênh ở v2)
- reference_id (reference, không FK — order_id hoặc refund_id tùy `type`, chỉ để trace/debug)
- payload (JSON — template variables đã snapshot, VD tên khách, mã đơn, số tiền — không query lại
  dữ liệu gốc lúc gửi để tránh trường hợp order đã đổi tiếp sau đó làm sai lệch nội dung email)
- status (enum: PENDING, SENT, FAILED)
- attempt_count (int, default 0), next_retry_at (nullable, default now())
- created_at, sent_at (nullable)
```

Index gợi ý: `notifications(status, next_retry_at)` — phục vụ trực tiếp query của scheduled worker.

### 8.6 Điểm cần bạn quyết định lại nếu không đồng ý

- Đã giả định **dùng Mailhog/SMTP giả lập cho dev, không cần domain email thật** — nếu muốn demo
  bằng email thật (VD portfolio cho nhà tuyển dụng tự thử), cần đăng ký SMTP provider thật (SendGrid
  free tier, Gmail SMTP...) và xử lý thêm rate limit của provider đó.
- Đã giả định **không có in-app notification list** — nếu muốn có, cần thêm field `read_at` và API
  `GET /api/notifications/me`, việc mở rộng không lớn nhưng là quyết định phạm vi cần xác nhận.

### 8.7 Ghi chú triển khai thực tế (khác nhỏ so với bản thiết kế ban đầu)

- **Package tên `notification`** giữ nguyên như thiết kế (không như module Return phải đổi tên do
  `return` là từ khóa Java).
- **`NotificationType` đặt ở package `notification.event`, không phải `notification.entity`** —
  vì Order/Payment module cần import enum này để publish event, và nguyên tắc "không import thẳng
  entity module khác" (mục 0.8) áp dụng chặt hơn cho package `entity`. Enum vẫn được tái dùng làm
  cột `notifications.type`, chỉ khác vị trí file.
- **`management.health.mail.enabled=false`** bắt buộc phải thêm — mặc định Spring Boot tự động gắn
  `MailHealthIndicator` vào `/actuator/health` khi có `spring-boot-starter-mail`, khiến health check
  toàn app trả `DOWN` (503) nếu Mailhog tạm thời không reachable. Đúng tinh thần mục 8.3 ("lỗi gửi
  email không được phép làm rollback nghiệp vụ chính") mở rộng sang cả health probe — mail là
  side-channel, không nên gate liveness/readiness của cả hệ thống.
- **`Notification.save()` không cần bean/`REQUIRES_NEW` riêng như `RefundLedger`/
  `ReturnRefundResultApplier`** — cập nhật trạng thái 1 dòng `notifications` là thao tác đơn-entity,
  `JpaRepository.save()` tự có transaction riêng cho chính nó; khác Return/Payout vì ở đó cần nhiều
  bước ghi atomic cùng lúc (restock + status + history).

## 9. v2 — Module Seller Payout (Phase 11)

### 9.1 Phạm vi v2 (ghi nhận công nợ — không tích hợp chuyển tiền thật)

**Giới hạn quan trọng nhất phải chốt trước khi thiết kế**: đây là project cá nhân, **không** tích
hợp cổng thanh toán ra (payout API thật của VNPay hay chuyển khoản ngân hàng) — phạm vi v2 dừng ở
**ghi nhận đúng số tiền seller được hưởng và trạng thái đã trả/chưa trả**, việc "trả tiền" là hành
động ngoài hệ thống (admin chuyển khoản thủ công rồi bấm xác nhận). Đây là quyết định thu hẹp scope
tương tự Coupon/Return đã làm, tránh biến project thành tích hợp payment-gateway lần 2.

- **Hoa hồng**: tỷ lệ % cố định toàn hệ thống (`PLATFORM_COMMISSION_RATE`, cấu hình qua
  `application.yml`, không phải theo từng seller/category ở v2 — per-seller rate là mở rộng v3).
- **Thời điểm ghi nhận công nợ**: khi `orders.status` chuyển `COMPLETED` (đúng nguồn sự thật đã dùng
  cho Report ở mục 0.9: đọc `order_status_history` chứ không phải `orders.updated_at`).
- **Chu kỳ payout**: theo lô (batch) do admin chủ động trigger cho 1 khoảng thời gian (VD "chốt sổ
  tháng 1"), **không** tự động chạy theo lịch cố định ở v2 — admin cần soát trước khi chốt (đặc biệt
  vì Return có thể phát sinh sau `COMPLETED`, xem 9.4).

### 9.2 User Flows & Edge Cases

- 1 order có sản phẩm từ nhiều seller → mỗi seller có 1 dòng ledger riêng cho phần của mình (không
  gộp), tính trên tổng `unit_price_snapshot * quantity` của các `order_item` thuộc seller đó trong
  order — nhất quán với việc `order_items` đã có sẵn `seller_id` reference từ v1 (mục 0, extension
  point đã chừa sẵn).
- Order `COMPLETED` rồi sau đó có Return được `REFUNDED` (mục 7) → ledger entry gốc của seller đó
  phải bị điều chỉnh giảm tương ứng, xem chi tiết 9.4 — đây là lý do v2 payout **không** tự động trả
  tiền ngay khi `COMPLETED`, phải chờ đủ return-window rồi mới generate batch (khuyến nghị admin đợi
  qua `RETURN_WINDOW_DAYS` của mục 7 rồi mới chốt sổ tháng, dù hệ thống không cấm chốt sớm hơn).
- Admin generate payout batch 2 lần cho cùng 1 khoảng thời gian → lần 2 chỉ gom các ledger entry
  `EARNED` **chưa** thuộc payout nào (`seller_ledger_entries.payout_id IS NULL`) — không double-pay,
  không cần validate thủ công khoảng ngày có bị trùng hay không.
- Seller bị khóa (`sellers.status = LOCKED`) nhưng vẫn còn ledger entry `EARNED` chưa trả → vẫn cho
  phép generate/trả payout bình thường (khóa tài khoản bán hàng không đồng nghĩa xóa nợ) — chỉ chặn
  seller đăng sản phẩm mới/nhận order mới, không liên quan tới công nợ cũ.
- Order bị hủy sau `COMPLETED` mà **không qua Return** (về lý thuyết không xảy ra theo state machine
  mục 0.6 — `SHIPPED`/`DELIVERED`/`COMPLETED` không cancel trực tiếp) → không cần xử lý, state
  machine đã chặn từ gốc.

### 9.3 Business Rules chốt

**`seller_ledger_entries`**: 1 dòng / (`order_id`, `seller_id`) — unique constraint chặn tạo trùng
nếu event `COMPLETED` vô tình publish 2 lần (idempotency, cùng tinh thần `payment_webhook_events`).
Mỗi dòng chốt cứng `gross_amount`, `commission_amount` (= `gross_amount * PLATFORM_COMMISSION_RATE`
tại thời điểm tạo — đổi rate sau này không ảnh hưởng ledger đã chốt), `net_amount = gross - commission`.

**`seller_payouts`**: 1 lần generate = 1 dòng, gom toàn bộ `seller_ledger_entries` đang
`status = EARNED AND payout_id IS NULL` **của 1 seller** trong khoảng `period_start..period_end`
(theo `order_status_history.created_at` của lần `COMPLETED`) vào 1 payout, set `payout_id` cho các
ledger entry đó, cộng tổng thành `seller_payouts.total_amount`. Payout tạo ra ở `status = PENDING`,
admin xác nhận đã chuyển khoản thật → `PAID` (`paid_at = now()`) — thao tác thủ công, không có
webhook nào cập nhật tự động vì không tích hợp cổng chuyển tiền thật (mục 9.1).

**Giao tiếp module (chốt tường minh, không để ngỏ như bản nháp đầu)**: `PayoutService.recordEarning
(orderId)` được `OrderService` gọi **trực tiếp qua service interface, trong cùng transaction** với
transition `→ COMPLETED` — không dùng `ApplicationEventPublisher` kiểu Notification (mục 8.3). Lý do
khác Notification: ghi nhận công nợ là dữ liệu tài chính cần đúng tuyệt đối (không được phép "thỉnh
thoảng miss 1 event" như trường hợp email lỡ không gửi thì cùng lắm khách không nhận được thư), nên
áp dụng đúng nguyên tắc đã chọn cho Coupon (mục 6.3: gọi trực tiếp, cùng transaction, vì đây thuần
là ghi DB không có network call). `orders.status → COMPLETED` có **2 điểm vào** cần gọi
`recordEarning()` — bắt buộc cả 2 nơi đều gọi, thiếu 1 trong 2 sẽ tạo lỗ hổng công nợ:
1. Scheduled job tự động (`OrderMaintenanceProcessor`, `DELIVERED → COMPLETED` sau 3 ngày).
2. Khách tự xác nhận nhận hàng sớm hơn (mục 0.6: "customer có thể tự xác nhận nhận hàng sớm hơn").

### 9.4 Tương tác với Return (mục 7) — điểm phức tạp nhất

Khi 1 Return được `REFUNDED` cho `order_item` có ledger entry tương ứng, xử lý theo đúng 3 trường
hợp của `payout_id` — **phải xét đủ cả 3, thiếu case giữa dễ làm sai lệch `total_amount` của 1
payout đã generate nhưng chưa trả**:

1. **`payout_id IS NULL`** (chưa gom vào payout nào) → chỉ cần đổi thẳng `status` của dòng gốc
   thành `VOIDED` — đơn giản nhất vì chưa có tổng nào đã tính phải sửa lại.
2. **`payout_id` trỏ tới 1 payout đang `PENDING`** (đã generate, admin chưa xác nhận trả) → **không**
   sửa trực tiếp dòng gốc (payout đã "chốt sổ" tại thời điểm generate, sửa ngầm sẽ làm
   `seller_payouts.total_amount` không còn khớp tổng các ledger entry thuộc nó — mất khả năng đối
   soát). Thay vào đó: tạo dòng `ADJUSTED` âm mới (`payout_id = NULL`) như case 3, và **trừ trực
   tiếp vào `seller_payouts.total_amount` của đúng payout `PENDING` đó** trong cùng transaction (đây
   là payout chưa trả tiền thật nên còn sửa được, khác hẳn case `PAID`).
3. **`payout_id` trỏ tới 1 payout đã `PAID`** (seller đã nhận tiền thật) → hệ thống **không** đòi lại
   tiền tự động (không có cơ chế thu hồi tiền đã chuyển khoản thật ở v2). Tạo 1 dòng
   `seller_ledger_entries` mới với `status = ADJUSTED`, `net_amount` âm (đúng bằng phần bị hoàn trả
   tương ứng của seller đó), `payout_id = NULL` — dòng âm này được gom vào batch payout **kế tiếp**
   của seller đó, trừ thẳng vào tổng tiền lần trả tiếp theo (giống nguyên tắc "trừ lương kỳ sau"
   thay vì đòi hoàn ngay).

Đây là lý do mục 7.2 ghi rõ "phải cài Return trước hoặc cùng lúc với Payout" — nếu cài Payout trước
mà chưa có Return, sẽ không có chỗ neo cho luồng `ADJUSTED` này, dễ dẫn tới thiết kế lại giữa chừng.

### 9.5 API Endpoints (đề xuất)

```
SELLER
GET    /api/seller/ledger                      (lịch sử ghi nhận công nợ của chính mình, pagination)
GET    /api/seller/payouts                     (lịch sử các lần đã/sẽ được trả, pagination)

ADMIN
POST   /api/admin/payouts/generate             (body: periodStart, periodEnd — tạo payout cho TẤT
                                                 CẢ seller có ledger entry EARNED trong kỳ, mỗi
                                                 seller 1 payout riêng)
GET    /api/admin/payouts                      (pagination, filter theo status/seller)
PATCH  /api/admin/payouts/{id}/mark-paid
```

### 9.6 Schema

```
seller_ledger_entries
- id (PK), seller_id (reference, không FK — cùng nhóm module giao dịch cốt lõi như order_items)
- order_id (reference, không FK), payout_id (nullable reference tới seller_payouts.id, không FK)
- gross_amount (numeric), commission_amount (numeric), net_amount (numeric)
- status (enum: EARNED, ADJUSTED, VOIDED)
- return_request_id (nullable, reference, không FK — chỉ có giá trị khi status = ADJUSTED, trace về
  return nào gây ra điều chỉnh)
- created_at

UNIQUE (order_id, seller_id) WHERE status = 'EARNED'   -- chặn double-ghi-nhận cho cùng 1 order/seller

seller_payouts
- id (PK), seller_id (reference, không FK)
- period_start, period_end, total_amount (numeric)
- status (enum: PENDING, PAID)
- paid_at (nullable), created_at
```

Ghi chú FK: `seller_id` ở đây **không** dùng FK dù `sellers` là module nền (khác với `products.seller_id`
vốn có FK thật) — vì `seller_ledger_entries`/`seller_payouts` bản thân là dữ liệu giao dịch/tài
chính biến động cao (cùng nhóm với Order/Payment), áp dụng quy tắc theo bản chất bảng đang xét chứ
không phải theo module đích tham chiếu tới. Cần bạn xác nhận lại cách hiểu này ở mục 9.7 nếu muốn
diễn giải khác.

### 9.7 Điểm cần bạn quyết định lại nếu không đồng ý

- Đã giả định **hoa hồng % cố định toàn hệ thống** — nếu cần theo từng seller/category, thêm cột
  `commission_rate` vào `sellers` hoặc `categories`, không đổi cấu trúc ledger.
- Đã giả định **không tích hợp chuyển tiền thật** (chỉ ghi nhận + đánh dấu thủ công) — nếu muốn tích
  hợp thật (VD VNPay có API chuyển tiền cho merchant), đây là tích hợp mới hoàn toàn, độ phức tạp
  tương đương module Payment ban đầu.
- Đã giả định **`seller_id` trong ledger/payout không FK** (xem 9.6) — nếu bạn cho rằng bảng tài
  chính vẫn nên FK cứng tới `sellers` vì đây không phải bảng "core transactional biến động" theo
  đúng tinh thần mục 0.8 (nó không có khả năng tách microservice độc lập như Order/Payment), có thể
  đổi lại — đây là điểm diễn giải quy tắc FK chưa từng gặp trước đó, cần chốt tường minh.

## 10. v2 — Shipment theo từng Seller (Phase 12, phác thảo sơ bộ)

### 10.1 Vì sao chỉ phác thảo, không thiết kế chi tiết ngay

Đây là thay đổi cấu trúc lớn nhất trong toàn bộ roadmap v2: nó phá vỡ giả định nền tảng
"1 order = 1 shipment" đã thấm vào nhiều quyết định v1 (xem danh sách phá vỡ ở 10.2). Thiết kế chi
tiết ngay bây giờ (trước khi Return/Notification/Payout ổn định) có rủi ro phải sửa lại nhiều lần vì
2 module đó cũng chạm tới đúng những bảng bị ảnh hưởng (`orders`, `order_items`, `deliveries`) — làm
lúc này dễ tạo ra thiết kế "đoán trước" sai. Quyết định: **giữ lại nguyên bản ghi chú roadmap, thiết
kế chi tiết đầy đủ (schema, state machine, migration) ngay trước khi bắt tay code module này**, sau
khi mục 7-9 đã triển khai và ổn định.

### 10.2 Những giả định v1 sẽ bị phá vỡ (ghi nhận trước, chưa giải quyết)

- **Aggregate rule `CONFIRMED → PACKED`** (mục 0.6: "100% order_items đã PACKED") phải tách theo
  từng nhóm seller — mỗi seller có shipment riêng, tiến độ đóng gói độc lập.
- **`deliveries` 1:1 với `orders`** (mục 0.9: `order_id UNIQUE`) phải đổi thành 1:N (1 order nhiều
  delivery, mỗi delivery gắn 1 seller).
- **Review eligibility suy từ `orders.status`** (mục 0.6: "vì v1 chỉ có 1 shipment/order... mọi item
  đủ điều kiện review cùng lúc") phải đổi sang tính theo trạng thái giao hàng thật của từng item.
- **Refund/Cancel theo order** (mục 0.6, 0.5 Flow 5: "refund một phần order... để v2") cần xác định
  lại đơn vị hủy — hủy theo shipment hay theo item, ảnh hưởng cách tính `refunds.amount`.
- **`order_status_history` cấp order** cần cân nhắc có cần thêm `order_status_history` cấp shipment
  hay tái dùng `delivery_status_history` đã có sẵn cấu trúc tương tự.

### 10.3 Hướng đi dự kiến (chưa chốt, chỉ để định hướng)

Nhiều khả năng cần 1 bảng trung gian `shipments` (thay thế vai trò hiện tại của `deliveries` ở cấp
"nhóm theo seller", `deliveries` giữ nguyên là chi tiết vận chuyển vật lý nhưng tham chiếu tới
`shipment_id` thay vì `order_id` trực tiếp) và tách `order_items.item_status` chi tiết hơn
(`PENDING/PACKED/SHIPPED/DELIVERED` ở cấp item thay vì chỉ `PENDING/PACKED`). Đây **không phải**
quyết định chốt — chỉ ghi lại hướng nghĩ để không bắt đầu lại từ số 0 khi tới lúc thiết kế thật.

## 11. v2 — Multi-currency (phác thảo sơ bộ, cân nhắc bỏ qua)

### 11.1 Đánh giá giá trị trước khi đầu tư thiết kế

Khác với 4 mục trên, multi-currency **không có nhu cầu nghiệp vụ thật** cho 1 project cá nhân
(không có khách hàng đa quốc gia thật) và giá trị phỏng vấn thấp hơn hẳn — "biết lưu currency code
cạnh amount" là kiến thức cơ bản, không phải bài toán khó đủ để kể chuyện behavioral như concurrency
hay idempotency đã có. Khuyến nghị: **cân nhắc bỏ hẳn mục này khỏi roadmap**, dành thời gian cho
việc đào sâu 4 module trên (VD viết thêm test, benchmark, tài liệu vận hành) thay vì dàn trải.

### 11.2 Nếu vẫn muốn làm (phạm vi tối thiểu, không có FX thật)

- Thêm cột `currency` (`CHAR(3)`, ISO 4217, default `'VND'`) vào `orders`, `payments`, `refunds`,
  `coupons` (cho `discount_value` khi `FIXED_AMOUNT`), `seller_ledger_entries`.
- **Không tích hợp tỷ giá hối đoái thật** (không gọi API tỷ giá, không quy đổi qua lại) — mỗi order
  chốt cứng 1 currency tại thời điểm tạo, toàn bộ tính toán liên quan (refund, ledger, coupon) đều
  cùng currency với order gốc, validate chặn nếu lệch (VD không cho refund USD cho payment VND).
- Đây chỉ là "gắn nhãn", không phải "hỗ trợ đa tiền tệ" theo nghĩa đầy đủ (chưa xử lý hiển thị theo
  ngôn ngữ/locale, chưa xử lý sàn giao dịch nào chấp nhận currency nào) — nếu tương lai cần thật,
  đây sẽ là 1 module riêng (`fx/` hoặc tích hợp bên thứ 3), không phải mở rộng nhỏ từ cột `currency`.

### 11.3 Cần bạn quyết định

- Có làm mục này không, hay bỏ khỏi roadmap? Nếu bỏ, cập nhật lại mục Roadmap (mục 5) để không còn
  liệt kê nó như 1 phase cần làm.
