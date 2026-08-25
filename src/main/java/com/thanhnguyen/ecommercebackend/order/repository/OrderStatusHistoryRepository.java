package com.thanhnguyen.ecommercebackend.order.repository;

import com.thanhnguyen.ecommercebackend.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
}
