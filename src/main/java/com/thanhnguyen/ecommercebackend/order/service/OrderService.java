package com.thanhnguyen.ecommercebackend.order.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemPayoutInfo;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemResponse;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemReturnInfo;
import com.thanhnguyen.ecommercebackend.order.dto.OrderResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {
    OrderResponse checkout(User currentUser, CheckoutRequest request);

    PageResponse<OrderResponse> listMyOrders(User currentUser, Pageable pageable);

    OrderResponse getMyOrder(User currentUser, Long orderId);

    OrderResponse cancel(User currentUser, Long orderId);

    /**
     * Admin force-cancel — dùng khi order đã PACKED trở đi (customer không tự hủy được nữa).
     * Không check ownership (admin có thể hủy order của bất kỳ customer nào).
     * Cho phép từ PENDING_PAYMENT/CONFIRMED/PACKED; trigger refund nếu order đã thanh toán (CONFIRMED/PACKED).
     */
    OrderResponse forceCancel(User admin, Long orderId);

    /**
     * Customer tự hủy phần hàng của 1 seller trong order (design doc v2 mục 10.5) — áp dụng từ
     * CONFIRMED trở đi (trước đó dùng cancel() hủy cả đơn). Chỉ cho phép khi seller đó chưa PACKED
     * xong toàn bộ item. Trigger refund một phần (prorate theo item của seller đó), không giải
     * phóng tồn kho (giống rule cancel sau CONFIRMED ở v1).
     */
    OrderResponse cancelSellerItems(User currentUser, Long orderId, Long sellerId);

    /**
     * Admin force-cancel phần hàng của 1 seller — cho phép cả khi seller đó đã PACKED xong (nhưng
     * chưa có delivery được assign), bất đối xứng với customer giống rule v1 (mục 0.6). Không check
     * ownership.
     */
    OrderResponse forceCancelSellerItems(User admin, Long orderId, Long sellerId);

    void expirePendingPayments();

    /** Đọc order theo id, không check ownership — dùng nội bộ bởi Payment module qua service interface. */
    OrderResponse getOrderById(Long orderId);

    /** Chuyển order từ PAYMENT_FAILED/PENDING_PAYMENT về PENDING_PAYMENT khi user tạo lại payment request. */
    void reopenForPayment(Long orderId);

    /** VNPay IPN xác nhận thanh toán thành công → PENDING_PAYMENT/PAYMENT_FAILED chuyển CONFIRMED, chốt bán tồn kho. */
    void confirmPayment(Long orderId);

    /** VNPay IPN báo thanh toán thất bại → PENDING_PAYMENT chuyển PAYMENT_FAILED (giữ nguyên reservation để retry). */
    void markPaymentFailed(Long orderId, String reason);

    /** Seller xem các order chứa item của mình — mỗi order chỉ trả về item thuộc seller đó. */
    PageResponse<OrderResponse> listSellerOrders(User currentUser, Pageable pageable);

    /**
     * Seller đóng gói 1 order item; nếu 100% item của seller đó trong order đã PACKED thì tính lại
     * aggregate-min qua recomputeAggregateStatus (design doc v2 mục 10.4).
     */
    OrderItemResponse packOrderItem(User currentUser, Long orderItemId);

    /** Dùng bởi Shipping module để gate assignShipper theo từng seller (mục 10.4). */
    boolean areSellerItemsPacked(Long orderId, Long sellerId);

    /**
     * Tính lại orders.status theo aggregate-min qua các seller (design doc v2 mục 10.4) — gọi sau
     * khi 1 seller pack xong toàn bộ item hoặc delivery của seller đó đổi trạng thái. Idempotent —
     * không đổi gì nếu rank tổng hợp không tăng. Không tác động nếu order đã ở trạng thái ngoài
     * vòng fulfillment (VD CANCELLED/COMPLETED).
     */
    void recomputeAggregateStatus(Long orderId);

    /**
     * Giao thất bại lần 2 (hết quyền retry) → hủy phần hàng của seller đó (item_status = CANCELLED),
     * tự động trigger refund một phần — design doc v2 mục 10.5. Không nhận actor: đây là hệ quả tự
     * động của business rule (hết quyền retry), không phải hành động trực tiếp của actor nào; lịch sử
     * thất bại giao hàng thật đã ghi ở delivery_status_history bởi ShippingService.
     */
    void markFailedDeliveryAndCancel(Long orderId, Long sellerId);

    /** Scheduled job: tự động hoàn tất order đã DELIVERED quá 3 ngày → COMPLETED. */
    void autoCompleteDeliveredOrders();

    /**
     * Dùng bởi Review module để verify quyền review (chỉ người đã mua và order đã DELIVERED/COMPLETED).
     * Trả về id của order gần nhất thỏa điều kiện, hoặc null nếu customer chưa từng mua/nhận productId này.
     */
    Long findEligibleOrderIdForReview(User customer, Long productId);

    /** Admin: xem toàn bộ order trong hệ thống. */
    PageResponse<OrderResponse> listAllOrders(Pageable pageable);

    /**
     * Dùng bởi Return module: đọc thông tin order_item cần cho validate eligibility + tính refund
     * (ownership, trạng thái order, thời điểm DELIVERED, total/discount để prorate — design doc v2
     * mục 7.3/7.4). Trả về null nếu order_item không tồn tại.
     */
    OrderItemReturnInfo getOrderItemForReturn(Long orderItemId);

    /**
     * Dùng bởi Notification module: resolve customerId từ orderId khi xử lý domain event (order
     * confirmed/shipped/delivered/cancelled/refunded) — design doc v2 mục 8.3. Trả về null nếu
     * order không tồn tại (phòng thủ — không nên xảy ra vì event chỉ publish sau khi order đã lưu).
     */
    Long getCustomerIdByOrderId(Long orderId);

    /**
     * Dùng bởi Payout module: đọc breakdown order_item (sellerId, unitPriceSnapshot, quantity) của
     * 1 order để tính EARNED ledger entry theo từng seller khi order → COMPLETED (design doc v2 mục
     * 9.3). Trả về danh sách rỗng nếu order không tồn tại (phòng thủ — không nên xảy ra vì chỉ gọi
     * ngay trong transaction chuyển COMPLETED).
     */
    List<OrderItemPayoutInfo> getOrderItemsForPayout(Long orderId);
}
