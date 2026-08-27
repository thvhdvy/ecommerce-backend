package com.thanhnguyen.ecommercebackend.payout.repository;

import com.thanhnguyen.ecommercebackend.payout.entity.SellerBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface SellerBalanceRepository extends JpaRepository<SellerBalance, Long> {
    Optional<SellerBalance> findBySellerId(Long sellerId);

    // Upsert nguyen tu (INSERT ... ON CONFLICT), khong doc-roi-ghi — cung tinh than conditional-UPDATE
    // voi InventoryRepository, nhung can insert-if-absent vi hang seller_balances duoc tao lazy o lan
    // EARNED/ADJUSTED dau tien cua seller (design doc v2 muc 9.3/9.6). amount co the am (ADJUSTED).
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO seller_balances (seller_id, balance, version, updated_at) "
            + "VALUES (:sellerId, :amount, 0, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (seller_id) DO UPDATE SET "
            + "balance = seller_balances.balance + :amount, "
            + "version = seller_balances.version + 1, "
            + "updated_at = CURRENT_TIMESTAMP",
            nativeQuery = true)
    void addToBalance(@Param("sellerId") Long sellerId, @Param("amount") BigDecimal amount);
}
