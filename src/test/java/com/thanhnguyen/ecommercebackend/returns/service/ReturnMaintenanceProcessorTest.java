package com.thanhnguyen.ecommercebackend.returns.service;

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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * expireOne() la logic that su cua job auto-expire (design doc v2 muc 7.2: APPROVED qua
 * RETURN_SHIP_BACK_DAYS ma khach khong gui hang tra -> EXPIRED, khong hoan tien). Truoc test nay
 * chi duoc verify o muc "co goi processor" (ReturnServiceImplTest), khong verify hanh vi ben trong.
 */
@ExtendWith(MockitoExtension.class)
class ReturnMaintenanceProcessorTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private ReturnStatusHistoryRepository returnStatusHistoryRepository;

    private ReturnMaintenanceProcessor processor;

    private static final Long RETURN_ID = 1L;
    private static final int SHIP_BACK_DAYS = 7;

    @BeforeEach
    void setUp() {
        processor = new ReturnMaintenanceProcessor(returnRequestRepository, returnStatusHistoryRepository);
    }

    private ReturnRequest returnRequest(ReturnRequestStatus status) {
        User customer = new User();
        customer.setId(1L);
        ReturnRequest r = new ReturnRequest(
                100L, 10L, customer, 5L, ReturnReason.DEFECTIVE, null, new BigDecimal("100.00"));
        r.setId(RETURN_ID);
        r.setStatus(status);
        return r;
    }

    @Test
    void expireOne_shouldTransitionToExpired_whenStillApproved() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.APPROVED);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(r));

        processor.expireOne(RETURN_ID, SHIP_BACK_DAYS);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.EXPIRED);
        verify(returnRequestRepository).save(r);
        verify(returnStatusHistoryRepository).save(argThat(h ->
                h.getFromStatus() == ReturnRequestStatus.APPROVED
                        && h.getToStatus() == ReturnRequestStatus.EXPIRED
                        && h.getChangedBy() == null
                        && h.getReason().contains(String.valueOf(SHIP_BACK_DAYS))));
    }

    @Test
    void expireOne_shouldNoOp_whenReturnRequestNotFound() {
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.empty());

        processor.expireOne(RETURN_ID, SHIP_BACK_DAYS);

        verify(returnRequestRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(returnStatusHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void expireOne_shouldNoOp_whenAlreadyMovedPastApproved() {
        // Khach da gui hang tra kip luc (ITEM_RECEIVED) truoc khi scheduled job kip chay -> khong duoc
        // ghi de thanh EXPIRED (design doc v2 muc 7.2, guard idempotent trong code).
        ReturnRequest r = returnRequest(ReturnRequestStatus.ITEM_RECEIVED);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(r));

        processor.expireOne(RETURN_ID, SHIP_BACK_DAYS);

        assertThat(r.getStatus()).isEqualTo(ReturnRequestStatus.ITEM_RECEIVED);
        verify(returnRequestRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(returnStatusHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
