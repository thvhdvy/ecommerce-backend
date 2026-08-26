package com.thanhnguyen.ecommercebackend.payment.service;

import com.thanhnguyen.ecommercebackend.order.dto.OrderResponse;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentDisputeResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentIntentResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentStatusResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.RefundResponse;
import com.thanhnguyen.ecommercebackend.payment.entity.Payment;
import com.thanhnguyen.ecommercebackend.payment.entity.PaymentStatus;
import com.thanhnguyen.ecommercebackend.payment.entity.PaymentWebhookEvent;
import com.thanhnguyen.ecommercebackend.payment.entity.Refund;
import com.thanhnguyen.ecommercebackend.payment.exception.PaymentNotAllowedException;
import com.thanhnguyen.ecommercebackend.payment.exception.PaymentNotFoundException;
import com.thanhnguyen.ecommercebackend.payment.repository.PaymentRepository;
import com.thanhnguyen.ecommercebackend.payment.repository.PaymentWebhookEventRepository;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayClient;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayQueryResult;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayRefundResult;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final Set<OrderStatus> PAYABLE_STATUSES =
            Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED);

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderService orderService;
    private final VnpayClient vnpayClient;
    private final RefundLedger refundLedger;

    @Override
    @Transactional
    public PaymentIntentResponse createPaymentIntent(User currentUser, Long orderId, String clientIp) {
        OrderResponse order = orderService.getMyOrder(currentUser, orderId);

        if (!PAYABLE_STATUSES.contains(order.getStatus())) {
            throw new PaymentNotAllowedException(
                    "Order is not payable in status " + order.getStatus());
        }

        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            orderService.reopenForPayment(orderId);
        }

        String txnRef = vnpayClient.generateTxnRef(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            payment = new Payment(orderId, txnRef, order.getTotalAmount());
        } else {
            payment.setVnpTxnRef(txnRef);
            payment.setVnpTransactionNo(null);
            payment.setAmount(order.getTotalAmount());
            payment.setStatus(PaymentStatus.PENDING);
        }
        Payment saved = paymentRepository.save(payment);

        String paymentUrl = vnpayClient.buildPaymentUrl(txnRef, saved.getAmount(), orderId, clientIp);
        return new PaymentIntentResponse(orderId, paymentUrl, saved.getAmount());
    }

    @Override
    @Transactional
    public PaymentStatusResponse getStatus(User currentUser, Long orderId, String clientIp) {
        orderService.getMyOrder(currentUser, orderId); // ownership check, throws if not found/owned
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment = reconcileWithVnpay(payment, clientIp);
        }

        return toStatusResponse(payment);
    }

    /**
     * Chu dong hoi VNPay (querydr) khi payment con PENDING — dung cho truong hop IPN bi tre/mat
     * (vd server chua co public URL de VNPay push IPN vao). Best-effort: loi ket noi/query khong
     * throw, chi tra ve trang thai hien tai trong DB — khong lam hong response cua user.
     */
    private Payment reconcileWithVnpay(Payment payment, String clientIp) {
        String orderInfo = "Thanh toan don hang " + payment.getOrderId();
        VnpayQueryResult result = vnpayClient.queryTransactionStatus(
                payment.getVnpTxnRef(), payment.getCreatedAt(), orderInfo, clientIp);

        if (!result.queried()) {
            log.warn("VNPay querydr khong ket luan duoc cho txnRef={}: {}",
                    payment.getVnpTxnRef(), result.transactionStatus());
            return payment;
        }

        // Dung chung 1 hang rao idempotency voi handleIpn — neu IPN that su toi truoc/sau,
        // 2 duong deu dung chung 1 vnp_transaction_no nen khong xu ly trung.
        int inserted = webhookEventRepository.insertIfAbsent(
                result.vnpTransactionNo(), "QUERY_RECONCILE", result.toString());
        if (inserted == 0) {
            return paymentRepository.findByOrderId(payment.getOrderId()).orElse(payment);
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

    @Override
    @Transactional
    public Map<String, String> handleIpn(Map<String, String> params) {
        if (!vnpayClient.verifySignature(params)) {
            log.warn("VNPay IPN invalid signature: {}", params.get("vnp_TxnRef"));
            return ipnResponse("97", "Invalid signature");
        }

        String txnRef = params.get("vnp_TxnRef");
        String transactionNo = params.get("vnp_TransactionNo");
        String responseCode = params.get("vnp_ResponseCode");

        Payment payment = paymentRepository.findByVnpTxnRef(txnRef).orElse(null);
        if (payment == null) {
            return ipnResponse("01", "Order not found");
        }

        BigDecimal ipnAmount = new BigDecimal(params.get("vnp_Amount")).movePointLeft(2);
        if (payment.getAmount().compareTo(ipnAmount) != 0) {
            log.warn("VNPay IPN amount mismatch for txnRef={}: expected={}, got={}",
                    txnRef, payment.getAmount(), ipnAmount);
            // Ghi nhan de admin tra cuu (Flow 10 - dispute) - khong tu doi PaymentStatus/OrderStatus,
            // chi/danh dau de dieu tra thu cong, tranh dung vao payment state machine dang chay on dinh
            // cho 1 case hiem (chu ky HMAC da hop le, chi lech so tien).
            webhookEventRepository.insertIfAbsent(transactionNo, "AMOUNT_MISMATCH", params.toString());
            return ipnResponse("04", "Invalid amount");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return ipnResponse("02", "Order already confirmed");
        }

        int inserted = webhookEventRepository.insertIfAbsent(transactionNo, "PAYMENT_RESULT", params.toString());
        if (inserted == 0) {
            return ipnResponse("00", "Confirm Success");
        }

        if ("00".equals(responseCode)) {
            applySuccess(payment, transactionNo);
        } else {
            applyFailure(payment, transactionNo, "VNPay responseCode=" + responseCode);
        }

        return ipnResponse("00", "Confirm Success");
    }

    // saveAndFlush (khong chi save): khi goi tu handleIpn, native @Modifying query insertIfAbsent()
    // truoc do khong duoc Hibernate track trong dirty-checking cua persistence context, nen thay doi
    // status sau do can flush tuong minh truoc khi orderService.confirmPayment() doc lai state —
    // khong the dua vao auto-flush luc commit.
    private Payment applySuccess(Payment payment, String vnpTransactionNo) {
        payment.setVnpTransactionNo(vnpTransactionNo);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        Payment saved = paymentRepository.saveAndFlush(payment);
        orderService.confirmPayment(payment.getOrderId());
        return saved;
    }

    private Payment applyFailure(Payment payment, String vnpTransactionNo, String reason) {
        payment.setVnpTransactionNo(vnpTransactionNo);
        payment.setStatus(PaymentStatus.FAILED);
        Payment saved = paymentRepository.saveAndFlush(payment);
        orderService.markPaymentFailed(payment.getOrderId(), reason);
        return saved;
    }

    // Khong @Transactional: chi orchestrate, khong tu ghi DB. requestRefund() la loi goi mang toi
    // VNPay, phai nam ngoai transaction — xem RefundLedger.
    @Override
    public void refund(Long orderId, String reason, String clientIp) {
        RefundLedger.RefundInitiation initiation = refundLedger.initiate(orderId, reason);

        VnpayRefundResult result = vnpayClient.requestRefund(
                initiation.vnpTxnRef(), initiation.vnpTransactionNo(), initiation.paymentCreatedAt(),
                initiation.amount(), reason, clientIp);

        refundLedger.finalizeResult(initiation.refundId(), orderId, result);
    }

    @Override
    public List<RefundResponse> listFailedRefunds() {
        return refundLedger.listFailedRefunds().stream().map(this::toRefundResponse).toList();
    }

    @Override
    public void resolveRefundManually(Long refundId, String note) {
        refundLedger.manuallyResolve(refundId, note);
    }

    @Override
    public List<PaymentDisputeResponse> listAmountMismatches() {
        return webhookEventRepository.findByEventTypeOrderByProcessedAtDesc("AMOUNT_MISMATCH").stream()
                .map(this::toDisputeResponse)
                .toList();
    }

    private PaymentDisputeResponse toDisputeResponse(PaymentWebhookEvent event) {
        return new PaymentDisputeResponse(
                event.getId(), event.getVnpTransactionNo(), event.getEventType(),
                event.getPayload(), event.getProcessedAt());
    }

    private RefundResponse toRefundResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(), refund.getOrderId(), refund.getAmount(), refund.getStatus(),
                refund.getReason(), refund.getResolutionNote(), refund.getCreatedAt(), refund.getUpdatedAt());
    }

    private PaymentStatusResponse toStatusResponse(Payment payment) {
        return new PaymentStatusResponse(
                payment.getOrderId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getVnpTransactionNo(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }

    private Map<String, String> ipnResponse(String rspCode, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("RspCode", rspCode);
        response.put("Message", message);
        return response;
    }
}
