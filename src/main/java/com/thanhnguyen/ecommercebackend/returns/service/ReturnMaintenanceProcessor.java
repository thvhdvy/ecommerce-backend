package com.thanhnguyen.ecommercebackend.returns.service;

import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequest;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequestStatus;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnStatusHistory;
import com.thanhnguyen.ecommercebackend.returns.repository.ReturnRequestRepository;
import com.thanhnguyen.ecommercebackend.returns.repository.ReturnStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xu ly TUNG return request trong 1 transaction rieng cho scheduled job auto-expire — cung mo hinh
 * voi OrderMaintenanceProcessor (1 request loi khong keo ca batch rollback; goi qua bean rieng de
 * @Transactional di qua proxy).
 */
@Component
@RequiredArgsConstructor
class ReturnMaintenanceProcessor {

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnStatusHistoryRepository returnStatusHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void expireOne(Long returnRequestId, int shipBackDays) {
        ReturnRequest r = returnRequestRepository.findById(returnRequestId).orElse(null);
        if (r == null || r.getStatus() != ReturnRequestStatus.APPROVED) {
            return; // da ITEM_RECEIVED/CANCELLED trong luc cho xu ly — bo qua
        }

        returnStatusHistoryRepository.save(new ReturnStatusHistory(
                r, ReturnRequestStatus.APPROVED, ReturnRequestStatus.EXPIRED, null,
                "Auto-expired: item not shipped back within " + shipBackDays + " days"));
        r.setStatus(ReturnRequestStatus.EXPIRED);
        returnRequestRepository.save(r);
    }
}
