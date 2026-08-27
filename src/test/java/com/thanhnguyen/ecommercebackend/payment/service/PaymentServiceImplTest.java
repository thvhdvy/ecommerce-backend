package com.thanhnguyen.ecommercebackend.payment.service;

import com.thanhnguyen.ecommercebackend.order.dto.OrderResponse;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.payment.entity.Payment;
import com.thanhnguyen.ecommercebackend.payment.entity.PaymentStatus;
import com.thanhnguyen.ecommercebackend.payment.entity.PaymentWebhookEvent;
import com.thanhnguyen.ecommercebackend.payment.entity.Refund;
import com.thanhnguyen.ecommercebackend.payment.entity.RefundStatus;
import com.thanhnguyen.ecommercebackend.payment.exception.PaymentNotAllowedException;
import com.thanhnguyen.ecommercebackend.payment.exception.PaymentNotFoundException;
import com.thanhnguyen.ecommercebackend.payment.exception.RefundNotAllowedException;
import com.thanhnguyen.ecommercebackend.payment.repository.PaymentRepository;
import com.thanhnguyen.ecommercebackend.payment.repository.PaymentWebhookEventRepository;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayClient;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayQueryResult;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayRefundResult;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private VnpayClient vnpayClient;

    @Mock
    private RefundLedger refundLedger;

    private PaymentServiceImpl paymentService;

    // PaymentResultApplier dung instance that (khong mock) boc quanh cung bo mock — de cac test
    // handleIpn/getStatus van assert duoc behavior cuoi cung (payment status doi, orderService
    // duoc goi) thay vi chi verify delegation.
    @BeforeEach
    void setUp() {
        PaymentResultApplier resultApplier =
                new PaymentResultApplier(paymentRepository, webhookEventRepository, orderService);
        paymentService = new PaymentServiceImpl(
                paymentRepository, webhookEventRepository, orderService, vnpayClient, refundLedger, resultApplier);
    }

    private final User customer = customer();

    private static User customer() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private OrderResponse orderResponse(OrderStatus status) {
        return new OrderResponse(
                10L, status, new BigDecimal("45.00"), "A", "0900000000", "addr", null, List.of(), null, null,
                null, BigDecimal.ZERO);
    }

    @Test
    void createPaymentIntent_shouldBuildUrl_whenOrderPendingPayment() {
        when(orderService.getMyOrder(customer, 10L)).thenReturn(orderResponse(OrderStatus.PENDING_PAYMENT));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        when(vnpayClient.generateTxnRef(10L)).thenReturn("10-999");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vnpayClient.buildPaymentUrl(eq("10-999"), any(), eq(10L), anyString()))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...");

        var response = paymentService.createPaymentIntent(customer, 10L, "127.0.0.1");

        assertThat(response.getOrderId()).isEqualTo(10L);
        assertThat(response.getPaymentUrl()).startsWith("https://sandbox.vnpayment.vn");
        verify(orderService, never()).reopenForPayment(anyLong());
    }

    @Test
    void createPaymentIntent_shouldReopenOrder_whenRetryingAfterFailed() {
        when(orderService.getMyOrder(customer, 10L)).thenReturn(orderResponse(OrderStatus.PAYMENT_FAILED));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(
                new Payment(10L, "10-111", new BigDecimal("45.00"))));
        when(vnpayClient.generateTxnRef(10L)).thenReturn("10-222");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vnpayClient.buildPaymentUrl(anyString(), any(), eq(10L), anyString())).thenReturn("url");

        paymentService.createPaymentIntent(customer, 10L, "127.0.0.1");

        verify(orderService).reopenForPayment(10L);
    }

    @Test
    void createPaymentIntent_shouldThrow_whenOrderNotPayable() {
        when(orderService.getMyOrder(customer, 10L)).thenReturn(orderResponse(OrderStatus.CONFIRMED));

        assertThatThrownBy(() -> paymentService.createPaymentIntent(customer, 10L, "127.0.0.1"))
                .isInstanceOf(PaymentNotAllowedException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleIpn_shouldConfirmOrder_whenSignatureValidAndResponseCodeSuccess() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        Map<String, String> params = ipnParams("10-999", "VNP-TXN-1", "00", "4500");

        when(vnpayClient.verifySignature(params)).thenReturn(true);
        when(paymentRepository.findByVnpTxnRef("10-999")).thenReturn(Optional.of(payment));
        when(webhookEventRepository.insertIfAbsent(eq("VNP-TXN-1"), anyString(), anyString())).thenReturn(1);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> response = paymentService.handleIpn(params);

        assertThat(response.get("RspCode")).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(orderService).confirmPayment(10L);
        verify(orderService, never()).markPaymentFailed(anyLong(), anyString());
    }

    @Test
    void handleIpn_shouldMarkFailed_whenResponseCodeNotSuccess() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        Map<String, String> params = ipnParams("10-999", "VNP-TXN-2", "24", "4500");

        when(vnpayClient.verifySignature(params)).thenReturn(true);
        when(paymentRepository.findByVnpTxnRef("10-999")).thenReturn(Optional.of(payment));
        when(webhookEventRepository.insertIfAbsent(eq("VNP-TXN-2"), anyString(), anyString())).thenReturn(1);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleIpn(params);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderService).markPaymentFailed(eq(10L), anyString());
        verify(orderService, never()).confirmPayment(anyLong());
    }

    @Test
    void handleIpn_shouldRejectInvalidSignature() {
        Map<String, String> params = ipnParams("10-999", "VNP-TXN-3", "00", "4500");
        when(vnpayClient.verifySignature(params)).thenReturn(false);

        Map<String, String> response = paymentService.handleIpn(params);

        assertThat(response.get("RspCode")).isEqualTo("97");
        verify(paymentRepository, never()).findByVnpTxnRef(anyString());
    }

    @Test
    void handleIpn_shouldRejectAmountMismatch() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        Map<String, String> params = ipnParams("10-999", "VNP-TXN-4", "00", "999900");

        when(vnpayClient.verifySignature(params)).thenReturn(true);
        when(paymentRepository.findByVnpTxnRef("10-999")).thenReturn(Optional.of(payment));

        Map<String, String> response = paymentService.handleIpn(params);

        assertThat(response.get("RspCode")).isEqualTo("04");
        verify(orderService, never()).confirmPayment(anyLong());
        verify(webhookEventRepository).insertIfAbsent(eq("VNP-TXN-4"), eq("AMOUNT_MISMATCH"), anyString());
    }

    @Test
    void handleIpn_shouldNotDoubleProcess_whenDuplicateTransactionNo() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        Map<String, String> params = ipnParams("10-999", "VNP-TXN-5", "00", "4500");

        when(vnpayClient.verifySignature(params)).thenReturn(true);
        when(paymentRepository.findByVnpTxnRef("10-999")).thenReturn(Optional.of(payment));
        when(webhookEventRepository.insertIfAbsent(eq("VNP-TXN-5"), anyString(), anyString())).thenReturn(0);

        Map<String, String> response = paymentService.handleIpn(params);

        assertThat(response.get("RspCode")).isEqualTo("00");
        verify(orderService, never()).confirmPayment(anyLong());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refund_shouldCallVnpayThenFinalize_afterLedgerInitiate() {
        RefundLedger.RefundInitiation initiation = new RefundLedger.RefundInitiation(
                7L, new BigDecimal("45.00"), "10-999", "VNP-TXN-1", java.time.LocalDateTime.now());
        when(refundLedger.initiate(10L, "customer cancelled")).thenReturn(initiation);
        VnpayRefundResult result = VnpayRefundResult.success("VNP-REFUND-1");
        when(vnpayClient.requestRefund(eq("10-999"), eq("VNP-TXN-1"), any(), eq(new BigDecimal("45.00")), anyString(), anyString()))
                .thenReturn(result);

        paymentService.refund(10L, "customer cancelled", "127.0.0.1");

        verify(refundLedger).finalizeResult(7L, 10L, result);
    }

    @Test
    void refund_shouldPropagateException_whenLedgerInitiateRejects() {
        when(refundLedger.initiate(10L, "reason"))
                .thenThrow(new RefundNotAllowedException("Payment not succeeded, cannot refund order 10"));

        assertThatThrownBy(() -> paymentService.refund(10L, "reason", "127.0.0.1"))
                .isInstanceOf(RefundNotAllowedException.class);

        verify(vnpayClient, never()).requestRefund(anyString(), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void getStatus_shouldThrow_whenPaymentNotFound() {
        when(orderService.getMyOrder(customer, 10L)).thenReturn(orderResponse(OrderStatus.PENDING_PAYMENT));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getStatus(customer, 10L, "127.0.0.1"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void getStatus_shouldNotReconcile_whenPaymentAlreadySucceeded() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(orderService.getMyOrder(customer, 10L)).thenReturn(orderResponse(OrderStatus.CONFIRMED));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));

        paymentService.getStatus(customer, 10L, "127.0.0.1");

        verify(vnpayClient, never()).queryTransactionStatus(anyString(), any(), anyString(), anyString());
    }

    @Test
    void getStatus_shouldConfirmOrder_whenQuerydrReportsSuccess() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        when(orderService.getMyOrder(customer, 10L)).thenReturn(orderResponse(OrderStatus.PENDING_PAYMENT));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));
        when(vnpayClient.queryTransactionStatus(eq("10-999"), any(), anyString(), anyString()))
                .thenReturn(new VnpayQueryResult(true, "00", "VNP-TXN-Q1"));
        when(webhookEventRepository.insertIfAbsent(eq("VNP-TXN-Q1"), anyString(), anyString())).thenReturn(1);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.getStatus(customer, 10L, "127.0.0.1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(orderService).confirmPayment(10L);
    }

    @Test
    void getStatus_shouldNotReapply_whenIpnAlreadyProcessedDuringQuerydr() {
        // Lan doc dau (getStatus): PENDING -> di hoi VNPay. Lan doc lai (applier, sau network call):
        // IPN da xu ly xong, status da SUCCEEDED -> khong ap lai, khong confirm order lan 2.
        Payment pendingSnapshot = new Payment(10L, "10-999", new BigDecimal("45.00"));
        Payment confirmedByIpn = new Payment(10L, "10-999", new BigDecimal("45.00"));
        confirmedByIpn.setStatus(PaymentStatus.SUCCEEDED);
        when(orderService.getMyOrder(customer, 10L)).thenReturn(orderResponse(OrderStatus.PENDING_PAYMENT));
        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.of(pendingSnapshot), Optional.of(confirmedByIpn));
        when(vnpayClient.queryTransactionStatus(eq("10-999"), any(), anyString(), anyString()))
                .thenReturn(new VnpayQueryResult(true, "00", "VNP-TXN-Q2"));

        var response = paymentService.getStatus(customer, 10L, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(orderService, never()).confirmPayment(anyLong());
        verify(webhookEventRepository, never()).insertIfAbsent(anyString(), anyString(), anyString());
    }

    @Test
    void getStatus_shouldNotChangeAnything_whenQuerydrCannotBeReached() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        when(orderService.getMyOrder(customer, 10L)).thenReturn(orderResponse(OrderStatus.PENDING_PAYMENT));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));
        when(vnpayClient.queryTransactionStatus(eq("10-999"), any(), anyString(), anyString()))
                .thenReturn(VnpayQueryResult.unknown("CONNECTION_ERROR"));

        var response = paymentService.getStatus(customer, 10L, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(orderService, never()).confirmPayment(anyLong());
        verify(orderService, never()).markPaymentFailed(anyLong(), anyString());
    }

    @Test
    void listFailedRefunds_shouldMapEntitiesToResponses() {
        Refund refund = new Refund(new Payment(10L, "10-999", new BigDecimal("45.00")), 10L, new BigDecimal("45.00"), "reason");
        refund.setId(7L);
        refund.setStatus(RefundStatus.REFUND_FAILED);
        when(refundLedger.listFailedRefunds(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(refund)));

        var result = paymentService.listFailedRefunds(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(7L);
        assertThat(result.getContent().get(0).getOrderId()).isEqualTo(10L);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(RefundStatus.REFUND_FAILED);
    }

    @Test
    void resolveRefundManually_shouldDelegateToLedger() {
        paymentService.resolveRefundManually(7L, "note");

        verify(refundLedger).manuallyResolve(7L, "note");
    }

    @Test
    void listAmountMismatches_shouldMapEventsToResponses() {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setId(1L);
        event.setVnpTransactionNo("VNP-TXN-4");
        event.setEventType("AMOUNT_MISMATCH");
        event.setPayload("{...}");
        event.setProcessedAt(java.time.LocalDateTime.now());
        when(webhookEventRepository.findByEventType(eq("AMOUNT_MISMATCH"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        var result = paymentService.listAmountMismatches(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getVnpTransactionNo()).isEqualTo("VNP-TXN-4");
        assertThat(result.getContent().get(0).getEventType()).isEqualTo("AMOUNT_MISMATCH");
    }

    private Map<String, String> ipnParams(String txnRef, String transactionNo, String responseCode, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_TransactionNo", transactionNo);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_Amount", amount);
        params.put("vnp_SecureHash", "irrelevant-mocked");
        return params;
    }
}
