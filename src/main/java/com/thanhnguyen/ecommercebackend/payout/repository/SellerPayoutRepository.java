package com.thanhnguyen.ecommercebackend.payout.repository;

import com.thanhnguyen.ecommercebackend.payout.entity.SellerPayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {
    Page<SellerPayout> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);
}
