package com.thanhnguyen.ecommercebackend.payment.repository;

import com.thanhnguyen.ecommercebackend.payment.entity.Refund;
import com.thanhnguyen.ecommercebackend.payment.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.payment.id = :paymentId AND r.status IN :statuses")
    BigDecimal sumAmountByPaymentIdAndStatusIn(
            @Param("paymentId") Long paymentId, @Param("statuses") Collection<RefundStatus> statuses);
}
