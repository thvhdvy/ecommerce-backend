package com.thanhnguyen.ecommercebackend.shipping.repository;

import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryStatusHistoryRepository extends JpaRepository<DeliveryStatusHistory, Long> {
}
