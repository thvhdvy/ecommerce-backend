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

- Tách shipment theo từng seller trong 1 order (thay vì 1 shipment/order).
- Seller payout / tính hoa hồng.
- Coupon / Promotion engine (giảm giá, điều kiện áp dụng, giới hạn lượt dùng có concurrency) — **đã thiết kế chi tiết ở mục 6**.
- Return / Exchange flow (state machine riêng, tương tác lại với refund).
- Notification service (email/push khi order đổi trạng thái).
- Multi-currency.

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
