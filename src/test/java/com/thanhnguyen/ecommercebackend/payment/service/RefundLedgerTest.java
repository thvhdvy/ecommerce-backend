package com.thanhnguyen.ecommercebackend.payment.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundLedgerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @InjectMocks
    private RefundLedger refundLedger;

    @Test
    void initiate_shouldThrow_whenPaymentNotFound() {
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundLedger.initiate(10L, "reason"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void initiate_shouldThrow_whenPaymentNotSucceeded() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> refundLedger.initiate(10L, "reason"))
                .isInstanceOf(RefundNotAllowedException.class);
    }

    @Test
    void initiate_shouldThrow_whenAlreadyFullyRefunded() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        payment.setId(5L);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumAmountByPaymentIdAndStatusIn(eq(5L), any()))
                .thenReturn(new BigDecimal("45.00"));

        assertThatThrownBy(() -> refundLedger.initiate(10L, "reason"))
                .isInstanceOf(RefundNotAllowedException.class);
    }

    @Test
    void initiate_shouldSaveRefundPending_andReturnRemainingAmount() {
        Payment payment = new Payment(10L, "10-999", new BigDecimal("45.00"));
        payment.setId(5L);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setVnpTransactionNo("VNP-TXN-1");
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumAmountByPaymentIdAndStatusIn(eq(5L), any())).thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any())).thenAnswer(inv -> {
            Refund refund = inv.getArgument(0);
            refund.setId(7L);
            return refund;
        });

        RefundLedger.RefundInitiation result = refundLedger.initiate(10L, "customer cancelled");

        assertThat(result.refundId()).isEqualTo(7L);
        assertThat(result.amount()).isEqualByComparingTo("45.00");
        assertThat(result.vnpTxnRef()).isEqualTo("10-999");
        assertThat(result.vnpTransactionNo()).isEqualTo("VNP-TXN-1");

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RefundStatus.REFUND_PENDING);
    }

    @Test
    void finalizeResult_shouldMarkRefunded_whenVnpaySucceeds() {
        Refund refund = new Refund(new Payment(10L, "10-999", new BigDecimal("45.00")), 10L, new BigDecimal("45.00"), "reason");
        when(refundRepository.findById(7L)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        refundLedger.finalizeResult(7L, 10L, VnpayRefundResult.success("VNP-REFUND-1"));

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(refund.getVnpRefundTransactionNo()).isEqualTo("VNP-REFUND-1");
    }

    @Test
    void finalizeResult_shouldMarkFailed_whenVnpayFails() {
        Refund refund = new Refund(new Payment(10L, "10-999", new BigDecimal("45.00")), 10L, new BigDecimal("45.00"), "reason");
        when(refundRepository.findById(7L)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        refundLedger.finalizeResult(7L, 10L, VnpayRefundResult.failure("91"));

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUND_FAILED);
    }

    @Test
    void listFailedRefunds_shouldDelegateToRepository() {
        Refund refund = new Refund(new Payment(10L, "10-999", new BigDecimal("45.00")), 10L, new BigDecimal("45.00"), "reason");
        when(refundRepository.findByStatus(RefundStatus.REFUND_FAILED)).thenReturn(List.of(refund));

        List<Refund> result = refundLedger.listFailedRefunds();

        assertThat(result).containsExactly(refund);
    }

    @Test
    void manuallyResolve_shouldThrow_whenRefundNotFound() {
        when(refundRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundLedger.manuallyResolve(7L, "note"))
                .isInstanceOf(RefundNotFoundException.class);
    }

    @Test
    void manuallyResolve_shouldThrow_whenRefundNotFailed() {
        Refund refund = new Refund(new Payment(10L, "10-999", new BigDecimal("45.00")), 10L, new BigDecimal("45.00"), "reason");
        refund.setStatus(RefundStatus.REFUNDED);
        when(refundRepository.findById(7L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> refundLedger.manuallyResolve(7L, "note"))
                .isInstanceOf(RefundNotAllowedException.class);
    }

    @Test
    void manuallyResolve_shouldMarkResolved_whenRefundFailed() {
        Refund refund = new Refund(new Payment(10L, "10-999", new BigDecimal("45.00")), 10L, new BigDecimal("45.00"), "reason");
        refund.setStatus(RefundStatus.REFUND_FAILED);
        when(refundRepository.findById(7L)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        refundLedger.manuallyResolve(7L, "Hoan tien tay qua chuyen khoan");

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUND_MANUALLY_RESOLVED);
        assertThat(refund.getResolutionNote()).isEqualTo("Hoan tien tay qua chuyen khoan");
    }
}
