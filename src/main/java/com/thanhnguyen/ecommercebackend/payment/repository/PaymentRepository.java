package com.thanhnguyen.ecommercebackend.payment.repository;

import com.thanhnguyen.ecommercebackend.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);
}
