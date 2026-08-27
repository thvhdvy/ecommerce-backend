package com.thanhnguyen.ecommercebackend.payout.repository;

import com.thanhnguyen.ecommercebackend.payout.entity.LedgerEntryType;
import com.thanhnguyen.ecommercebackend.payout.entity.SellerLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerLedgerEntryRepository extends JpaRepository<SellerLedgerEntry, Long> {
    Page<SellerLedgerEntry> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    // Idempotency guard: check truoc khi insert de tranh vi pham unique constraint giua-transaction
    // (mot loi constraint that su se danh dau ca transaction la abort tren Postgres) — design doc v2 muc 9.3.
    boolean existsByOrderIdAndType(Long orderId, LedgerEntryType type);
}
