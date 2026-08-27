package com.thanhnguyen.ecommercebackend.order.repository;

import com.thanhnguyen.ecommercebackend.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> findAllByOrderIdAndSellerId(Long orderId, Long sellerId);

    /**
     * Dung de verify quyen review: liet ke ung vien theo thu tu order moi nhat truoc, service layer
     * tu kiem tra tung ung vien da duoc seller tuong ung giao hang chua (design doc v2 muc 10.6 —
     * khong con suy dien tu Order_StatusIn nhu v1 vi delivery gio tach theo tung seller).
     */
    List<OrderItem> findAllByProductIdAndOrder_CustomerIdOrderByOrder_CreatedAtDesc(
            Long productId, Long customerId);
}
