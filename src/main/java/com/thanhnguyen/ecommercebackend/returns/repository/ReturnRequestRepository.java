package com.thanhnguyen.ecommercebackend.returns.repository;

import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequest;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    Page<ReturnRequest> findAllByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ReturnRequest> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    List<ReturnRequest> findAllByOrderId(Long orderId);

    // Batch scan cho scheduled job auto-expire — cung mo hinh voi
    // OrderRepository.findIdsByStatusAndUpdatedAtBefore (khong load ca entity, chi id).
    @Query("SELECT r.id FROM ReturnRequest r WHERE r.status = :status AND r.expiresAt < :cutoff ORDER BY r.id")
    List<Long> findIdsByStatusAndExpiresAtBefore(
            @Param("status") ReturnRequestStatus status, @Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
