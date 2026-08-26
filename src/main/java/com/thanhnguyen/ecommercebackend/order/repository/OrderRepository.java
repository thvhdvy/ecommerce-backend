package com.thanhnguyen.ecommercebackend.order.repository;

import com.thanhnguyen.ecommercebackend.order.entity.Order;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Sort lay tu Pageable (controller default createdAt DESC) — khop composite index
    // idx_orders_customer_id_created_at (V13, xem docs/query-optimization.md muc 3).
    Page<Order> findAllByCustomerId(Long customerId, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    // Chi lay id (khong load entity + items) cho scheduled job: moi order duoc xu ly lai trong
    // transaction rieng (OrderMaintenanceProcessor doc lai entity), va Pageable gioi han batch size
    // moi lan chay — backlog lon sau downtime khong keo ca bang vao memory.
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.createdAt < :cutoff ORDER BY o.id")
    List<Long> findIdsByStatusAndCreatedAtBefore(
            @Param("status") OrderStatus status, @Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.updatedAt < :cutoff ORDER BY o.id")
    List<Long> findIdsByStatusAndUpdatedAtBefore(
            @Param("status") OrderStatus status, @Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    /**
     * Order co chua it nhat 1 item cua seller — paginate o cap order (khong phai cap item) de
     * "1 order nhieu item cung seller" khong bi tach qua 2 trang. JPQL noi bo module Order
     * (Order + OrderItem cung module, khong vi pham boundary).
     */
    @Query("SELECT o FROM Order o WHERE EXISTS "
            + "(SELECT 1 FROM OrderItem i WHERE i.order = o AND i.sellerId = :sellerId)")
    Page<Order> findAllContainingSellerItems(@Param("sellerId") Long sellerId, Pageable pageable);
}
