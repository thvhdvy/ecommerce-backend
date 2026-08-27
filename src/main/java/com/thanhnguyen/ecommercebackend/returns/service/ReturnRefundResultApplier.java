package com.thanhnguyen.ecommercebackend.returns.service;

import com.thanhnguyen.ecommercebackend.payout.service.PayoutService;
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
 * Tach phan ghi DB (chuyen REFUND_PENDING/REFUND_FAILED/REFUNDED) ra khoi loi goi mang toi VNPay —
 * cung ly do voi RefundLedger (payment module): goi qua bean rieng de @Transactional di qua proxy
 * (self-invocation trong ReturnServiceImpl khong mo transaction moi), va REQUIRES_NEW vi apply()
 * duoc goi tu sau khi transaction goc (markItemReceived) da commit xong (design doc v2 muc 7.3 —
 * bug da tung xay ra that trong repo nay, xem commit daf5c49).
 */
@Component
@RequiredArgsConstructor
class ReturnRefundResultApplier {

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnStatusHistoryRepository returnStatusHistoryRepository;
    private final PayoutService payoutService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void applyResult(Long returnRequestId, boolean success) {
        ReturnRequest r = returnRequestRepository.findById(returnRequestId).orElse(null);
        if (r == null || r.getStatus() != ReturnRequestStatus.REFUND_PENDING) {
            return; // idempotent guard — da xu ly hoac trang thai da doi
        }

        ReturnRequestStatus newStatus = success ? ReturnRequestStatus.REFUNDED : ReturnRequestStatus.REFUND_FAILED;
        returnStatusHistoryRepository.save(
                new ReturnStatusHistory(r, ReturnRequestStatus.REFUND_PENDING, newStatus, null, null));
        r.setStatus(newStatus);
        returnRequestRepository.save(r);

        // Tru vao seller_balances ngay khi refund thanh cong — cung transaction, goi truc tiep qua
        // service interface (design doc v2 muc 9.4). Khong lam khi refund that bai (REFUND_FAILED) vi
        // tien chua thuc su hoan tra khach.
        if (success) {
            payoutService.recordAdjustment(r.getId(), r.getSellerId(), r.getRefundAmountSnapshot());
        }
    }

    // Admin retry: dua REFUND_FAILED ve lai REFUND_PENDING truoc khi thu goi VNPay lan nua —
    // REFUND_FAILED khong phai terminal (design doc muc 7.2).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markPendingForRetry(Long returnRequestId) {
        ReturnRequest r = returnRequestRepository.findById(returnRequestId).orElse(null);
        if (r == null || r.getStatus() != ReturnRequestStatus.REFUND_FAILED) {
            return;
        }
        returnStatusHistoryRepository.save(new ReturnStatusHistory(
                r, ReturnRequestStatus.REFUND_FAILED, ReturnRequestStatus.REFUND_PENDING, null, "Admin retry"));
        r.setStatus(ReturnRequestStatus.REFUND_PENDING);
        returnRequestRepository.save(r);
    }
}
