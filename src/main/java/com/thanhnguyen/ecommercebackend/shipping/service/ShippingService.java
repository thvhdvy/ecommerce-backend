package com.thanhnguyen.ecommercebackend.shipping.service;

import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.util.List;

public interface ShippingService {
    /**
     * Admin gán (hoặc gán lại) shipper cho 1 order đã PACKED/đang SHIPPED.
     * @param actor admin thực hiện hành động — ghi vào delivery_status_history/order_status_history.changed_by để audit.
     */
    DeliveryResponse assignShipper(Long orderId, Long shipperId, User actor);

    /** Shipper xem các delivery được gán cho mình. */
    PageResponse<DeliveryResponse> listMyDeliveries(User currentUser, Pageable pageable);

    /** Shipper cập nhật trạng thái giao hàng (IN_TRANSIT/DELIVERED/FAILED). */
    DeliveryResponse updateDeliveryStatus(User currentUser, Long deliveryId, DeliveryStatusUpdateRequest request);

    /**
     * Admin xác nhận giao hàng thành công thủ công khi shipper đã giao nhưng hệ thống không nhận
     * được cập nhật (app crash, mất mạng...) — đối soát qua báo cáo ngoài hệ thống (Flow 4).
     */
    DeliveryResponse confirmDeliveredManually(User admin, Long orderId);
}
