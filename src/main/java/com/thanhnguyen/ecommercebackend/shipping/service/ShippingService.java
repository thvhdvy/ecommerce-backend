package com.thanhnguyen.ecommercebackend.shipping.service;

import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatus;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.time.LocalDateTime;
import java.util.Map;

public interface ShippingService {
    /**
     * Admin gán (hoặc gán lại) shipper cho phần hàng của 1 seller trong order — mỗi seller có
     * delivery riêng (design doc v2 mục 10). Gate: 100% order_items của seller đó đã PACKED.
     * @param actor admin thực hiện hành động — ghi vào delivery_status_history.changed_by để audit.
     */
    DeliveryResponse assignShipper(Long orderId, Long sellerId, Long shipperId, User actor);

    /** Shipper xem các delivery được gán cho mình. */
    PageResponse<DeliveryResponse> listMyDeliveries(User currentUser, Pageable pageable);

    /** Shipper cập nhật trạng thái giao hàng (IN_TRANSIT/DELIVERED/FAILED). */
    DeliveryResponse updateDeliveryStatus(User currentUser, Long deliveryId, DeliveryStatusUpdateRequest request);

    /**
     * Admin xác nhận giao hàng thành công thủ công khi shipper đã giao nhưng hệ thống không nhận
     * được cập nhật (app crash, mất mạng...) — đối soát qua báo cáo ngoài hệ thống (Flow 4).
     */
    DeliveryResponse confirmDeliveredManually(User admin, Long orderId, Long sellerId);

    /**
     * Dùng bởi OrderService để tính aggregate-min orders.status qua các seller (design doc v2 mục
     * 10.4). Trả về map rỗng nếu order chưa có delivery nào được tạo.
     */
    Map<Long, DeliveryStatus> getDeliveryStatusesBySeller(Long orderId);

    /** Dùng bởi Review module để verify eligibility theo delivery thật của seller (mục 10.6). */
    boolean isDeliveredForSeller(Long orderId, Long sellerId);

    /** Dùng bởi Return module thay cho suy diễn order_status_history cấp order (mục 10.6). */
    LocalDateTime getDeliveredAtForSeller(Long orderId, Long sellerId);
}
