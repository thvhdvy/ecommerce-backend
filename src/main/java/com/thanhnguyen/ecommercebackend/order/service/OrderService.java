package com.thanhnguyen.ecommercebackend.order.service;

import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemResponse;
import com.thanhnguyen.ecommercebackend.order.dto.OrderResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.util.List;

public interface OrderService {
    OrderResponse checkout(User currentUser, CheckoutRequest request);

    List<OrderResponse> listMyOrders(User currentUser);

    OrderResponse getMyOrder(User currentUser, Long orderId);

    OrderResponse cancel(User currentUser, Long orderId);

    /**
     * Admin force-cancel — dùng khi order đã PACKED trở đi (customer không tự hủy được nữa).
     * Không check ownership (admin có thể hủy order của bất kỳ customer nào).
     * Cho phép từ PENDING_PAYMENT/CONFIRMED/PACKED; trigger refund nếu order đã thanh toán (CONFIRMED/PACKED).
     */
    OrderResponse forceCancel(User admin, Long orderId);

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
    List<OrderResponse> listSellerOrders(User currentUser);

    /** Seller đóng gói 1 order item; nếu 100% item của order đã PACKED thì order tự chuyển CONFIRMED → PACKED. */
    OrderItemResponse packOrderItem(User currentUser, Long orderItemId);

    /**
     * Admin gán shipper lần đầu cho order đã PACKED → chuyển PACKED → SHIPPED. Idempotent nếu đã SHIPPED (trường hợp gán lại shipper).
     * @param actor admin thực hiện hành động gán shipper — ghi vào order_status_history.changed_by để audit.
     */
    void markShipped(Long orderId, User actor);

    /**
     * Shipper báo giao hàng thành công → SHIPPED → DELIVERED.
     * @param actor shipper thực hiện hành động — ghi vào order_status_history.changed_by để audit.
     */
    void markDelivered(Long orderId, User actor);

    /**
     * Giao thất bại lần 1 (còn quyền retry) → ghi nhận SHIPPED → FAILED_DELIVERY → SHIPPED (tự động retry, không đổi shipper).
     * @param actor shipper báo cáo giao thất bại — ghi vào entry SHIPPED → FAILED_DELIVERY; entry retry tự động (FAILED_DELIVERY → SHIPPED) vẫn để null vì đó là quyết định hệ thống, không phải hành động của actor.
     */
    void markFailedDeliveryAndRetry(Long orderId, User actor);

    /**
     * Giao thất bại lần 2 (hết quyền retry) → SHIPPED → FAILED_DELIVERY → CANCELLED, tự động trigger refund.
     * @param actor shipper báo cáo giao thất bại lần 2 — ghi vào entry SHIPPED → FAILED_DELIVERY; entry auto-cancel (FAILED_DELIVERY → CANCELLED) vẫn để null vì đó là quyết định hệ thống.
     */
    void markFailedDeliveryAndCancel(Long orderId, User actor);

    /** Scheduled job: tự động hoàn tất order đã DELIVERED quá 3 ngày → COMPLETED. */
    void autoCompleteDeliveredOrders();

    /**
     * Dùng bởi Review module để verify quyền review (chỉ người đã mua và order đã DELIVERED/COMPLETED).
     * Trả về id của order gần nhất thỏa điều kiện, hoặc null nếu customer chưa từng mua/nhận productId này.
     */
    Long findEligibleOrderIdForReview(User customer, Long productId);

    /** Admin: xem toàn bộ order trong hệ thống. */
    List<OrderResponse> listAllOrders();
}
