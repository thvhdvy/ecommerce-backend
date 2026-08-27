package com.thanhnguyen.ecommercebackend.notification.repository;

import com.thanhnguyen.ecommercebackend.notification.entity.Notification;
import com.thanhnguyen.ecommercebackend.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("SELECT n.id FROM Notification n WHERE n.status = :status AND n.nextRetryAt <= :now ORDER BY n.id")
    List<Long> findIdsDueForDispatch(
            @Param("status") NotificationStatus status, @Param("now") LocalDateTime now, Pageable pageable);

    Page<Notification> findAllByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);
}
