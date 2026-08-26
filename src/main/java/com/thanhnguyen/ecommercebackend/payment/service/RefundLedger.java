package com.thanhnguyen.ecommercebackend.payment.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.thanhnguyen.ecommercebackend.payment.entity.Payment;
import com.thanhnguyen.ecommercebackend.payment.entity.PaymentStatus;
import com.thanhnguyen.ecommercebackend.payment.entity.Refund;
import com.thanhnguyen.ecommercebackend.payment.entity.RefundStatus;
import com.thanhnguyen.ecommercebackend.payment.exception.PaymentNotFoundException;
import com.thanhnguyen.ecommercebackend.payment.exception.RefundNotAllowedException;
import com.thanhnguyen.ecommercebackend.payment.exception.RefundNotFoundException;
import com.thanhnguyen.ecommercebackend.payment.repository.PaymentRepository;
import com.thanhnguyen.ecommercebackend.payment.repository.RefundRepository;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayRefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tach phan ghi DB cua refund ra khoi loi goi mang toi VNPay (xem PaymentServiceImpl.refund()).
 * initiate()/finalizeResult() la 2 transaction doc lap, ngan cach boi 1 lenh goi VNPay khong nam
 * trong bat ky transaction nao — tranh giu row lock/connection DB trong luc cho VNPay tra ve.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class RefundLedger {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    // REQUIRES_NEW: refund() co the duoc goi tu TransactionSynchronization.afterCommit() (xem
    // OrderServiceImpl.refundAfterCommit) — tai thoi diem do transaction goc vua commit xong,
    // resource cua no khong con dang tin cay de "join" (Spring khuyen nghi REQUIRES_NEW cho moi
    // thao tac transactional kich hoat tu afterCommit/afterCompletion).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    RefundInitiation initiate(Long orderId, String reason) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new RefundNotAllowedException("Payment not succeeded, cannot refund order " + orderId);
        }

        BigDecimal alreadyRefunded = refundRepository.sumAmountByPaymentIdAndStatusIn(
                payment.getId(), List.of(
                        RefundStatus.REFUND_PENDING, RefundStatus.REFUNDED, RefundStatus.REFUND_MANUALLY_RESOLVED));
        BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RefundNotAllowedException("Payment for order " + orderId + " already fully refunded");
        }

        Refund refund = refundRepository.save(new Refund(payment, orderId, remaining, reason));
        return new RefundInitiation(refund.getId(), remaining,
                payment.getVnpTxnRef(), payment.getVnpTransactionNo(), payment.getCreatedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void finalizeResult(Long refundId, Long orderId, VnpayRefundResult result) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalStateException("Refund not found: " + refundId));

        if (result.success()) {
            refund.setStatus(RefundStatus.REFUNDED);
            refund.setVnpRefundTransactionNo(result.vnpTransactionNo());
        } else {
            refund.setStatus(RefundStatus.REFUND_FAILED);
            log.error("VNPay refund failed for order {}: responseCode={}", orderId, result.responseCode());
        }
        refundRepository.save(refund);
    }

    // Chi doc + ghi DB, khong goi mang -> khong can REQUIRES_NEW, transaction thuong cua request la du.
    @Transactional
    Page<Refund> listFailedRefunds(Pageable pageable) {
        return refundRepository.findByStatus(RefundStatus.REFUND_FAILED, pageable);
    }

    // Admin xac nhan da hoan tien cho khach bang kenh khac (chuyen khoan tay...) sau khi VNPay tu choi
    // vinh vien (vd qua han hoan tien) - khong goi lai VNPay, chi ghi nhan lai trong he thong.
    @Transactional
    void manuallyResolve(Long refundId, String note) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException(refundId));

        if (refund.getStatus() != RefundStatus.REFUND_FAILED) {
            throw new RefundNotAllowedException(
                    "Refund " + refundId + " is not in REFUND_FAILED status, cannot mark resolved manually");
        }

        refund.setStatus(RefundStatus.REFUND_MANUALLY_RESOLVED);
        refund.setResolutionNote(note);
        refundRepository.save(refund);
    }

    record RefundInitiation(
            Long refundId, BigDecimal amount, String vnpTxnRef, String vnpTransactionNo,
            LocalDateTime paymentCreatedAt) {
    }
}
