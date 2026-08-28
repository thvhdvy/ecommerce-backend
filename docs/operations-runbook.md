# Operations Runbook — 5 module v2

Tài liệu vận hành cho Coupon, Return, Notification, Payout, Shipment-split — trả lời "hệ thống báo lỗi/kẹt
thì làm gì", không phải tài liệu kiến trúc (xem [`README.md`](../README.md)) hay thiết kế
(xem [`ecommerce-backend-design.md`](../ecommerce-backend-design.md)). Định dạng mỗi mục: **Triệu chứng
→ Chẩn đoán → Xử lý**.

## Scheduled jobs — tổng quan

Mọi job đều theo cùng 1 mô hình: mỗi record xử lý trong 1 transaction riêng (`REQUIRES_NEW` qua bean
Maintenance/Processor riêng), 1 record lỗi chỉ log + bỏ qua, không chặn cả batch.

| Job | File | Interval | Việc gì |
|---|---|---|---|
| `NotificationDispatchScheduler` | [`notification/scheduler`](../src/main/java/com/thanhnguyen/ecommercebackend/notification/scheduler/NotificationDispatchScheduler.java) | 60s (`fixedRate`) | Gửi email `PENDING` đã tới `next_retry_at`, batch 100 |
| `OrderTimeoutScheduler` | [`order/scheduler`](../src/main/java/com/thanhnguyen/ecommercebackend/order/scheduler/OrderTimeoutScheduler.java) | 60s | Hết hạn `PENDING_PAYMENT` quá 15 phút → `PAYMENT_EXPIRED`, release tồn kho/coupon |
| `OrderAutoCompleteScheduler` | [`order/scheduler`](../src/main/java/com/thanhnguyen/ecommercebackend/order/scheduler/OrderAutoCompleteScheduler.java) | 1h | `DELIVERED` quá 3 ngày → `COMPLETED`, gọi `PayoutService.recordEarning()` |
| `ReturnMaintenanceScheduler` | [`returns/scheduler`](../src/main/java/com/thanhnguyen/ecommercebackend/returns/scheduler/ReturnMaintenanceScheduler.java) | 1h | `APPROVED` quá 7 ngày chưa `ITEM_RECEIVED` → `EXPIRED` |

**Kiểm tra job có đang chạy không**: không có endpoint riêng — dấu hiệu gián tiếp là log định kỳ mỗi
interval (logger theo tên class ở trên) và số dòng `PENDING`/`APPROVED` quá hạn không tăng dần không kiểm
soát. Nếu app restart nhiều lần trong ngày (container orchestration), job vẫn tự chạy lại theo lịch —
không có state cần khôi phục giữa các lần chạy (mỗi lần quét lại từ DB).

## Coupon

### Redemption bị kẹt ở `RESERVED` dù order đã `PAYMENT_EXPIRED`/`CANCELLED`

- **Triệu chứng**: `coupons.usage_reserved` không giảm dù order rõ ràng đã hủy trước khi thanh toán.
- **Chẩn đoán**: `release()` chỉ được gọi từ `OrderServiceImpl` khi order chuyển `PAYMENT_EXPIRED`/
  `CANCELLED` **trước khi có payment thành công**. Kiểm tra `coupon_redemptions.status` của order đó —
  nếu vẫn `RESERVED` mà order đã terminal, có khả năng `OrderTimeoutScheduler` (60s) chưa kịp chạy, hoặc
  1 nhánh cancel nào đó quên gọi `CouponService.release()`.
- **Xử lý**: không có endpoint admin release thủ công ở v2 (ghi nhận thiếu, xem mục "Chưa có công cụ vận
  hành" cuối file). Tạm thời: chờ `OrderTimeoutScheduler` (nếu order chưa terminal), hoặc sửa tay
  `coupon_redemptions.status='RELEASED'` + `coupons.usage_reserved = usage_reserved - 1` qua DB trực tiếp
  (thao tác thủ công ngoài luồng, cần thận trọng vì bỏ qua conditional-update).

### Admin sửa coupon nhận lỗi 409 `COUPON_CONCURRENT_MODIFICATION`

- **Triệu chứng**: `PATCH /api/admin/coupons/{id}` trả 409.
- **Chẩn đoán**: bình thường, không phải lỗi hệ thống — 2 request sửa cùng 1 coupon gần như đồng thời
  (VD admin bấm 2 lần, hoặc dữ liệu vừa bị 1 request khác ghi đè), `@Version` optimistic lock chặn ghi đè
  ngầm.
- **Xử lý**: `GET /api/admin/coupons` đọc lại bản mới nhất, sửa lại, gửi lại request. Không retry mù —
  cần đọc lại state hiện tại trước khi gửi lại (dữ liệu có thể đã đổi).

### Coupon usage_limit đã hạ nhưng vẫn còn slot cho request cũ

- **Triệu chứng**: admin hạ `usage_limit` xuống thấp hơn `usage_reserved + usage_committed` hiện tại,
  nhưng các reservation cũ vẫn hợp lệ.
- **Chẩn đoán**: đây là hành vi đúng theo thiết kế, không phải bug — hạ `usage_limit` chỉ chặn **request
  mới** (`reserveUsage()` conditional `UPDATE ... WHERE usage_reserved + usage_committed < usage_limit`),
  không hủy retroactive các reservation đã tồn tại.
- **Xử lý**: không cần làm gì, giải thích đúng cho stakeholder nếu bị hỏi.

## Return

### `REFUND_FAILED` không tự retry

- **Triệu chứng**: `return_requests.status = 'REFUND_FAILED'` đứng yên, khách phàn nàn chưa nhận tiền.
- **Chẩn đoán**: đúng thiết kế — `REFUND_FAILED` không có scheduled job tự retry (khác `PAYMENT_EXPIRED`
  hay auto-expire), cần admin can thiệp chủ động vì lỗi refund thường do nguyên nhân bên ngoài (VNPay
  sandbox lỗi, sai thông tin) cần xác nhận trước khi thử lại.
- **Xử lý**: `POST /api/admin/returns/{id}/refund/retry` — đưa về `REFUND_PENDING`, lần quét job tiếp
  theo (hoặc gọi ngay trong transaction retry, tùy implementation) sẽ thử gọi VNPay refund lại. Nếu vẫn
  fail liên tục, kiểm tra `docs/` hoặc log VNPay response code cụ thể trước khi retry mù nhiều lần.

### Return request treo ở `REQUESTED`, seller không xử lý

- **Triệu chứng**: khách chờ lâu, seller không `approve`/`reject`.
- **Chẩn đoán**: v2 không có SLA/auto-escalate cho bước này (chỉ có auto-expire cho `APPROVED → EXPIRED`,
  không có cho `REQUESTED`).
- **Xử lý**: `POST /api/admin/returns/{id}/force-approve` — admin duyệt thay seller, bỏ qua check
  ownership (`ReturnServiceImplTest.forceApprove_shouldNotCheckOwnership`).

### Return bị `REFUNDED` sau khi seller đã được payout cho đúng order đó

- **Triệu chứng**: seller đã nhận tiền (`seller_payouts`), sau đó return của đơn đó được duyệt hoàn tiền.
- **Chẩn đoán**: đây là luồng bình thường đã có xử lý tự động — `ReturnRefundResultApplier.applyResult()`
  gọi thẳng `PayoutService.recordAdjustment()` khi refund thành công, trừ ngay vào `seller_balances`,
  **không chặn ở 0** (số dư seller có thể xuống âm — nghĩa là seller đang nợ ngược platform).
- **Xử lý**: không cần can thiệp, số âm tự bù trừ vào lần `EARNED` tiếp theo của seller đó. Nếu seller
  hỏi "sao balance âm", giải thích đây là cơ chế "trừ lương kỳ sau" tự động qua cộng dồn ledger — xem
  `GET /api/seller/ledger` để seller tự đối chiếu từng dòng `EARNED`/`ADJUSTED`.

## Notification

### Email không tới khách hàng (dev/demo, dùng Mailhog)

- **Triệu chứng**: khách báo không nhận được email, hoặc QA không thấy email khi test.
- **Chẩn đoán theo thứ tự**:
  1. Vào Mailhog UI `http://localhost:8025` — email có xuất hiện ở đó không? Nếu có, vấn đề chỉ là môi
     trường dev không gửi email thật (đúng thiết kế, xem [README](../README.md#tech-stack)) — không phải bug.
  2. Nếu không thấy trong Mailhog: `GET /api/admin/notifications?status=PENDING` — email còn kẹt chưa
     gửi? Kiểm tra `next_retry_at` có nằm trong tương lai không (đang chờ backoff).
  3. `GET /api/admin/notifications?status=FAILED` — đã vượt `MAX_ATTEMPTS=5`, dừng vĩnh viễn.
- **Xử lý**: với `FAILED`, `POST /api/admin/notifications/{id}/retry` — reset `attempt_count=0`,
  `next_retry_at=now()`, job lượt quét tiếp theo (≤60s) sẽ nhặt lại.

### `attempt_count` tăng nhanh, nhiều dòng `FAILED`

- **Triệu chứng**: số `FAILED` tăng bất thường trong thời gian ngắn.
- **Chẩn đoán**: khả năng cao SMTP (Mailhog hoặc provider thật ở production) đang down/reject toàn bộ —
  không phải lỗi riêng lẻ từng email. Backoff hiện tại: `2^attempt` phút (2, 4, 8, 16, 32 phút) — với
  `MAX_ATTEMPTS=5`, 1 email chỉ được thử trong ~1 giờ trước khi `FAILED` vĩnh viễn.
- **Xử lý**: kiểm tra kết nối SMTP trước (không phải trong app) — `docker compose logs mailhog` hoặc
  provider dashboard nếu production. Sau khi khắc phục kết nối, các dòng `FAILED` cần **retry thủ công
  từng dòng** qua endpoint trên (không có bulk-retry ở v2, xem mục cuối file).

### `/actuator/health` báo DOWN nghi do mail

- **Triệu chứng**: health check fail, nghi ngờ do Notification/SMTP.
- **Chẩn đoán**: **không phải** — `management.health.mail.enabled=false` đã tắt cố ý (xem
  [README](../README.md#observability)), SMTP down không được phép kéo `DOWN` cả liveness probe. Nếu
  health vẫn DOWN, nguyên nhân ở chỗ khác (DB, disk...).
- **Xử lý**: `GET /actuator/health` xem chi tiết component nào DOWN, không phải mail.

## Payout

### Seller khiếu nại số dư (`balance`) sai

- **Triệu chứng**: seller cho rằng `GET /api/seller/balance` không khớp kỳ vọng.
- **Chẩn đoán**: `seller_balances.balance` chỉ là **số tổng hợp** — nguồn sự thật đầy đủ nằm ở
  `seller_ledger_entries` (append-only, không sửa/xóa được). Đối soát bằng cách seller tự xem
  `GET /api/seller/ledger` (mọi dòng `EARNED`/`ADJUSTED` kèm `order_id`/`return_request_id` để trace).
  Balance = tổng cộng dồn `net_amount` chưa trả — cộng tay lại từ ledger phải khớp balance hiện tại, nếu
  lệch mới là bug thật (không nên xảy ra vì mọi ghi balance đều conditional `UPDATE` cùng transaction
  với insert ledger entry).
- **Xử lý**: nếu ledger cộng dồn khớp `balance` → giải thích cho seller (thường do quên tính return đã
  trừ). Nếu lệch thật → cần điều tra sâu hơn, không có công cụ tự động đối soát ở v2.

### `POST /api/admin/sellers/{id}/payouts` trả `PAYOUT_NOT_ALLOWED`

- **Triệu chứng**: admin bấm trả tiền cho seller nhưng bị từ chối.
- **Chẩn đoán 2 nhánh**:
  1. `balance <= 0` — không có gì để trả (seller đang nợ ngược platform do return, xem mục Return ở
     trên) — không phải lỗi.
  2. `ObjectOptimisticLockingFailureException` bị bắt và dịch lại thành `PayoutNotAllowedException` —
     2 request pay-out cùng lúc cho cùng 1 seller (hiếm, nhưng có thể xảy ra nếu admin bấm 2 lần).
- **Xử lý**: check `GET /api/admin/sellers/{id}/balance` trước khi kết luận — nếu dương, thử lại 1 lần
  (nhánh 2). Nếu vẫn fail liên tục dù balance dương, cần điều tra thêm (không nên xảy ra).

## Shipping (per-seller)

### Order không lên trạng thái dù 1 seller đã `DELIVERED`

- **Triệu chứng**: khách hỏi sao đơn chưa "giao xong" dù 1 phần hàng đã tới.
- **Chẩn đoán**: **đúng thiết kế** — `orders.status` là aggregate-min qua tất cả seller (design doc mục
  10.4). Order chỉ lên `DELIVERED` khi **toàn bộ** seller (chưa bị hủy) đều `DELIVERED`. Kiểm tra
  `GET /api/admin/orders/{id}` hoặc tra trực tiếp `deliveries WHERE order_id = ?` — sẽ thấy rõ seller nào
  đang là "seller chậm nhất" giữ order lại.
- **Xử lý**: không phải bug, giải thích cho khách seller nào còn đang xử lý. Nếu 1 seller ì ạch quá lâu,
  đó là vấn đề vận hành (nhắc seller), không phải vấn đề kỹ thuật.

### Delivery `FAILED` 2 lần liên tiếp — hàng không tới được, tiền có tự hoàn không?

- **Triệu chứng**: shipper báo `FAILED` lần 2 cho cùng 1 delivery.
- **Chẩn đoán**: đúng thiết kế, tự động xử lý — lần fail đầu tự động `ASSIGNED` lại để retry
  (`MAX_RETRY=1`), lần fail thứ 2 (retry đã hết) → `orderService.markFailedDeliveryAndCancel()`: item
  của seller đó chuyển `CANCELLED`, refund prorate tự động qua `PaymentService.refundPartial()`.
- **Xử lý**: kiểm tra `GET /api/admin/refunds/failed` — nếu refund tự động này lại thất bại (VNPay lỗi),
  nó vào chung hàng đợi `REFUND_FAILED` như refund thường (khác Return: đây là refund cấp order, không
  gắn `return_request_id`). Retry qua `POST /api/admin/orders/{id}/refund/retry` (gọi lại VNPay thật).
  Nếu đã xử lý ngoài hệ thống (VD chuyển khoản tay) và chỉ muốn đóng record, dùng
  `PATCH /api/admin/refunds/{id}/resolve` thay vì retry — 2 endpoint này khác mục đích, không phải cùng
  1 hành động.

## Chưa có công cụ vận hành (ghi nhận, không phải bug)

- **Không có bulk-retry** cho notification `FAILED` hay coupon reservation kẹt — mọi retry đều
  per-record qua endpoint riêng. Ở quy mô production thật, nên thêm endpoint bulk hoặc dashboard lọc
  theo `attempt_count`/`status` để xử lý hàng loạt khi SMTP/VNPay down diện rộng.
- **Không có endpoint admin release coupon reservation thủ công** — case "coupon kẹt RESERVED bất thường"
  ở trên hiện phải sửa tay qua DB, chưa có API.
- **Không có dashboard đối soát ledger tự động** cho Payout — đối soát hiện tại là đọc `seller_ledger_entries`
  bằng mắt qua API phân trang.

Cả 3 điểm trên là giới hạn có chủ đích của v2 (ưu tiên đúng luồng nghiệp vụ chính trước công cụ vận hành
phụ trợ) — ghi nhận ở đây để nếu câu hỏi phỏng vấn hỏi "hệ thống còn thiếu gì về mặt vận hành", có câu trả
lời rõ ràng thay vì ngạc nhiên.
