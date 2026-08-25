package com.thanhnguyen.ecommercebackend.order.repository;

import com.thanhnguyen.ecommercebackend.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findAllByOrder_IdOrderByCreatedAtDesc(Long orderId);
}
