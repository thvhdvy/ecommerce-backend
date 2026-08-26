package com.thanhnguyen.ecommercebackend.payment.service;

import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.payment.entity.Payment;
import com.thanhnguyen.ecommercebackend.payment.entity.PaymentStatus;
import com.thanhnguyen.ecommercebackend.payment.exception.PaymentNotFoundException;
import com.thanhnguyen.ecommercebackend.payment.repository.PaymentRepository;
import com.thanhnguyen.ecommercebackend.payment.repository.PaymentWebhookEventRepository;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ap ket qua thanh toan (tu IPN hoac querydr reconcile) vao Payment + Order trong 1 transaction.
 * Tach ra component rieng (cung ly do voi RefundLedger): getStatus() phai goi VNPay querydr
 * NGOAI transaction — khong giu connection DB trong khi cho network call (toi da 10s) — roi moi
 * mo transaction ngan de ap ket qua. Goi qua bean rieng de @Transactional di qua proxy
 * (self-invocation trong cung class khong mo transaction).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class PaymentResultApplier {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderService orderService;

    /**
     * Ap ket qua querydr sau khi da hoi VNPay xong (ngoai transaction). Doc lai payment tu DB
     * de lay trang thai moi nhat — IPN co the da den va xu ly xong trong luc cho querydr.
     */
    @Transactional
    Payment applyQueryResult(Long orderId, VnpayQueryResult result) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment; // IPN da xu ly trong luc cho querydr — khong ap lai
        }

        // Dung chung 1 hang rao idempotency voi handleIpn — neu IPN that su toi truoc/sau,
        // 2 duong deu dung chung 1 vnp_transaction_no nen khong xu ly trung.
        int inserted = webhookEventRepository.insertIfAbsent(
                result.vnpTransactionNo(), "QUERY_RECONCILE", result.toString());
        if (inserted == 0) {
            return payment;
        }

        if (result.isSuccess()) {
            return applySuccess(payment, result.vnpTransactionNo());
        }
        // "01" (dang xu ly) khong ket luan gi; cac ma khac coi nhu that bai
        if (!"01".equals(result.transactionStatus())) {
            return applyFailure(payment, result.vnpTransactionNo(),
                    "VNPay querydr transactionStatus=" + result.transactionStatus());
        }
        return payment;
    }

    // saveAndFlush (khong chi save): khi goi tu handleIpn, native @Modifying query insertIfAbsent()
    // truoc do khong duoc Hibernate track trong dirty-checking cua persistence context, nen thay doi
    // status sau do can flush tuong minh truoc khi orderService.confirmPayment() doc lai state —
    // khong the dua vao auto-flush luc commit.
    @Transactional
    Payment applySuccess(Payment payment, String vnpTransactionNo) {
        payment.setVnpTransactionNo(vnpTransactionNo);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        Payment saved = paymentRepository.saveAndFlush(payment);
        orderService.confirmPayment(payment.getOrderId());
        return saved;
    }

    @Transactional
    Payment applyFailure(Payment payment, String vnpTransactionNo, String reason) {
        payment.setVnpTransactionNo(vnpTransactionNo);
        payment.setStatus(PaymentStatus.FAILED);
        Payment saved = paymentRepository.saveAndFlush(payment);
        orderService.markPaymentFailed(payment.getOrderId(), reason);
        return saved;
    }
}
