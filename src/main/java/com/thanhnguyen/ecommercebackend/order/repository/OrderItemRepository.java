package com.thanhnguyen.ecommercebackend.order.repository;

import com.thanhnguyen.ecommercebackend.order.entity.OrderItem;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> findAllBySellerIdOrderByOrder_CreatedAtDesc(Long sellerId);

    /** Dung de verify quyen review: customer da tung mua productId nay va order da DELIVERED/COMPLETED chua. */
    Optional<OrderItem> findFirstByProductIdAndOrder_CustomerIdAndOrder_StatusInOrderByOrder_CreatedAtDesc(
            Long productId, Long customerId, Collection<OrderStatus> statuses);
}
