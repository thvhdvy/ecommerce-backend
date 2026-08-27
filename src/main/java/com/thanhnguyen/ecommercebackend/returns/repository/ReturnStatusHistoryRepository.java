package com.thanhnguyen.ecommercebackend.returns.repository;

import com.thanhnguyen.ecommercebackend.returns.entity.ReturnStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReturnStatusHistoryRepository extends JpaRepository<ReturnStatusHistory, Long> {
    List<ReturnStatusHistory> findAllByReturnRequest_IdOrderByCreatedAtDesc(Long returnRequestId);
}
