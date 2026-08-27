package com.thanhnguyen.ecommercebackend.shipping.repository;

import com.thanhnguyen.ecommercebackend.shipping.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByOrderIdAndSellerId(Long orderId, Long sellerId);

    List<Delivery> findAllByOrderId(Long orderId);

    Page<Delivery> findAllByShipperId(Long shipperId, Pageable pageable);
}
