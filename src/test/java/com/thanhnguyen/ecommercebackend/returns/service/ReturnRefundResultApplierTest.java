package com.thanhnguyen.ecommercebackend.returns.service;

import com.thanhnguyen.ecommercebackend.payout.service.PayoutService;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnReason;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequest;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequestStatus;
import com.thanhnguyen.ecommercebackend.returns.repository.ReturnRequestRepository;
import com.thanhnguyen.ecommercebackend.returns.repository.ReturnStatusHistoryRepository;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * applyResult() la noi thuc su goi PayoutService.recordAdjustment() (design doc v2 muc 9.4 — diem
 * noi quan trong nhat giua Return va Payout). Truoc test nay chi duoc verify o muc "co goi
 * refundResultApplier.applyResult(...)" (ReturnServiceImplTest), khong verify viec tru ledger seller
 * co thuc su xay ra hay khong, va khong verify guard idempotent + nhanh that bai khong tru ledger.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRefundResultApplierTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private ReturnStatusHistoryRepository returnStatusHistoryRepository;
    @Mock
    private PayoutService payoutService;

    private ReturnRefundResultApplier applier;

    private static final Long RETURN_ID = 1L;
    private static final Long SELLER_ID = 5L;

    @BeforeEach
    void setUp() {
        applier = new ReturnRefundResultApplier(returnRequestRepository, returnStatusHistoryRepository, payoutService);
    }

    private ReturnRequest returnRequest(ReturnRequestStatus status) {
        User customer = new User();
        customer.setId(1L);
        ReturnRequest r = new ReturnRequest(
                100L, 10L, customer, SELLER_ID, ReturnReason.DEFECTIVE, null, new BigDecimal("80.00"));
        r.setId(RETURN_ID);
        r.setStatus(status);
        return r;
    }

    // ---------- applyResult() ----------

    @Test
    void applyResult_shouldMarkRefunded_andRecordPayoutAdjustment_whenSuccess() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REFUND_PENDING);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(r));

        applier.applyResult(RETURN_ID, true);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.REFUNDED);
        verify(returnStatusHistoryRepository).save(argThat(h ->
                h.getFromStatus() == ReturnRequestStatus.REFUND_PENDING
                        && h.getToStatus() == ReturnRequestStatus.REFUNDED));
        verify(payoutService).recordAdjustment(RETURN_ID, SELLER_ID, new BigDecimal("80.00"));
    }

    @Test
    void applyResult_shouldMarkRefundFailed_andNotTouchPayoutLedger_whenPaymentFails() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REFUND_PENDING);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(r));

        applier.applyResult(RETURN_ID, false);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.REFUND_FAILED);
        verify(payoutService, never()).recordAdjustment(any(), any(), any());
    }

    @Test
    void applyResult_shouldNoOp_whenReturnRequestNotFound() {
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.empty());

        applier.applyResult(RETURN_ID, true);

        verify(returnRequestRepository, never()).save(any());
        verify(payoutService, never()).recordAdjustment(any(), any(), any());
    }

    @Test
    void applyResult_shouldNoOp_whenNotInRefundPending() {
        // Idempotent guard — VD 2 lan goi trung (retry ha tang) cho cung 1 ket qua thanh cong, lan 2
        // phai la no-op de khong tru ledger 2 lan cho cung 1 return request.
        ReturnRequest r = returnRequest(ReturnRequestStatus.REFUNDED);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(r));

        applier.applyResult(RETURN_ID, true);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.REFUNDED);
        verify(returnRequestRepository, never()).save(any());
        verify(payoutService, never()).recordAdjustment(any(), any(), any());
    }

    // ---------- markPendingForRetry() ----------

    @Test
    void markPendingForRetry_shouldTransitionToRefundPending_whenRefundFailed() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REFUND_FAILED);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(r));

        applier.markPendingForRetry(RETURN_ID);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.REFUND_PENDING);
        verify(returnStatusHistoryRepository).save(argThat(h ->
                h.getFromStatus() == ReturnRequestStatus.REFUND_FAILED
                        && h.getToStatus() == ReturnRequestStatus.REFUND_PENDING));
    }

    @Test
    void markPendingForRetry_shouldNoOp_whenNotRefundFailed() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REFUNDED);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(r));

        applier.markPendingForRetry(RETURN_ID);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.REFUNDED);
        verify(returnRequestRepository, never()).save(any());
    }
}
