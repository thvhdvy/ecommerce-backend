package com.thanhnguyen.ecommercebackend.returns.service;

import com.thanhnguyen.ecommercebackend.inventory.service.InventoryService;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemReturnInfo;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.order.exception.OrderItemNotFoundException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderOwnershipException;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnCreateRequest;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnResponse;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnReason;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequest;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequestStatus;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnAlreadyActiveException;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnNotEligibleException;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnNotFoundException;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnOwnershipException;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnStatusNotAllowedException;
import com.thanhnguyen.ecommercebackend.returns.repository.ReturnRequestRepository;
import com.thanhnguyen.ecommercebackend.returns.repository.ReturnStatusHistoryRepository;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.SellerStatus;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnServiceImplTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ReturnStatusHistoryRepository returnStatusHistoryRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private SellerService sellerService;

    @Mock
    private ReturnRefundResultApplier refundResultApplier;

    @Mock
    private ReturnMaintenanceProcessor maintenanceProcessor;

    @InjectMocks
    private ReturnServiceImpl returnService;

    private final User customer = user(1L);
    private final User otherUser = user(2L);

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Seller seller(Long id) {
        Seller s = new Seller();
        s.setId(id);
        s.setStatus(SellerStatus.ACTIVE);
        return s;
    }

    private OrderItemReturnInfo deliverableInfo(BigDecimal unitPrice, int qty, BigDecimal totalAmount, BigDecimal discountAmount) {
        return new OrderItemReturnInfo(
                10L, 100L, customer.getId(), 5L, 50L, "Product X", unitPrice, qty,
                OrderStatus.DELIVERED, totalAmount, discountAmount, LocalDateTime.now().minusDays(1));
    }

    private ReturnRequest returnRequest(ReturnRequestStatus status) {
        ReturnRequest r = new ReturnRequest(
                100L, 10L, customer, 5L, ReturnReason.DEFECTIVE, null, new BigDecimal("100.00"));
        r.setId(1L);
        r.setStatus(status);
        return r;
    }

    // ---------- create() ----------

    @Test
    void create_shouldSaveReturnRequest_whenEligible_noDiscount() {
        OrderItemReturnInfo info = deliverableInfo(new BigDecimal("50.00"), 2, new BigDecimal("100.00"), BigDecimal.ZERO);
        when(orderService.getOrderItemForReturn(10L)).thenReturn(info);
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = returnService.create(customer, new ReturnCreateRequest(10L, ReturnReason.DEFECTIVE, null));

        // Khong dung coupon -> discountRatio = 1, refund = unitPrice * qty = 50*2 = 100.
        assertThat(response.getRefundAmountSnapshot()).isEqualByComparingTo("100.00");
        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.REQUESTED);
        verify(returnStatusHistoryRepository).save(any());
    }

    @Test
    void create_shouldProrateDiscount_whenOrderUsedCoupon() {
        // order totalAmount=90 (sau discount), discountAmount=10 -> ratio = 90/100 = 0.9
        // item gross = 50*2=100 -> refund = 100*0.9 = 90.00
        OrderItemReturnInfo info = deliverableInfo(new BigDecimal("50.00"), 2, new BigDecimal("90.00"), new BigDecimal("10.00"));
        when(orderService.getOrderItemForReturn(10L)).thenReturn(info);
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = returnService.create(customer, new ReturnCreateRequest(10L, ReturnReason.DEFECTIVE, null));

        assertThat(response.getRefundAmountSnapshot()).isEqualByComparingTo("90.00");
    }

    @Test
    void create_shouldThrow_whenOrderItemNotFound() {
        when(orderService.getOrderItemForReturn(10L)).thenReturn(null);

        assertThatThrownBy(() -> returnService.create(customer, new ReturnCreateRequest(10L, ReturnReason.DEFECTIVE, null)))
                .isInstanceOf(OrderItemNotFoundException.class);
    }

    @Test
    void create_shouldThrow_whenNotOwner() {
        OrderItemReturnInfo info = deliverableInfo(new BigDecimal("50.00"), 1, new BigDecimal("50.00"), BigDecimal.ZERO);
        when(orderService.getOrderItemForReturn(10L)).thenReturn(info);

        assertThatThrownBy(() -> returnService.create(otherUser, new ReturnCreateRequest(10L, ReturnReason.DEFECTIVE, null)))
                .isInstanceOf(OrderOwnershipException.class);
        verify(returnRequestRepository, never()).save(any());
    }

    @Test
    void create_shouldThrow_whenOrderNotDeliveredOrCompleted() {
        OrderItemReturnInfo info = new OrderItemReturnInfo(
                10L, 100L, customer.getId(), 5L, 50L, "Product X", new BigDecimal("50.00"), 1,
                OrderStatus.SHIPPED, new BigDecimal("50.00"), BigDecimal.ZERO, null);
        when(orderService.getOrderItemForReturn(10L)).thenReturn(info);

        assertThatThrownBy(() -> returnService.create(customer, new ReturnCreateRequest(10L, ReturnReason.DEFECTIVE, null)))
                .isInstanceOf(ReturnNotEligibleException.class);
    }

    @Test
    void create_shouldThrow_whenReturnWindowExpired() {
        OrderItemReturnInfo info = new OrderItemReturnInfo(
                10L, 100L, customer.getId(), 5L, 50L, "Product X", new BigDecimal("50.00"), 1,
                OrderStatus.DELIVERED, new BigDecimal("50.00"), BigDecimal.ZERO, LocalDateTime.now().minusDays(10));
        when(orderService.getOrderItemForReturn(10L)).thenReturn(info);

        assertThatThrownBy(() -> returnService.create(customer, new ReturnCreateRequest(10L, ReturnReason.DEFECTIVE, null)))
                .isInstanceOf(ReturnNotEligibleException.class);
    }

    @Test
    void create_shouldThrow_whenAnotherActiveRequestExists() {
        OrderItemReturnInfo info = deliverableInfo(new BigDecimal("50.00"), 1, new BigDecimal("50.00"), BigDecimal.ZERO);
        when(orderService.getOrderItemForReturn(10L)).thenReturn(info);
        when(returnRequestRepository.save(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> returnService.create(customer, new ReturnCreateRequest(10L, ReturnReason.DEFECTIVE, null)))
                .isInstanceOf(ReturnAlreadyActiveException.class);
    }

    // ---------- cancel() ----------

    @Test
    void cancel_shouldTransitionToCancelled_whenRequested() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REQUESTED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = returnService.cancel(customer, 1L);

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.CANCELLED);
    }

    @Test
    void cancel_shouldThrow_whenNotOwner() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REQUESTED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> returnService.cancel(otherUser, 1L)).isInstanceOf(ReturnOwnershipException.class);
    }

    @Test
    void cancel_shouldThrow_whenNotRequested() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.APPROVED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> returnService.cancel(customer, 1L)).isInstanceOf(ReturnStatusNotAllowedException.class);
    }

    @Test
    void cancel_shouldThrow_whenNotFound() {
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.cancel(customer, 1L)).isInstanceOf(ReturnNotFoundException.class);
    }

    // ---------- approve()/reject() ----------

    @Test
    void approve_shouldSetApprovedAtAndExpiresAt_whenOwnedByRequestedSeller() {
        Seller seller = seller(5L);
        when(sellerService.requireActiveSeller(99L)).thenReturn(seller);
        ReturnRequest r = returnRequest(ReturnRequestStatus.REQUESTED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = returnService.approve(user(99L), 1L);

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(response.getApprovedAt()).isNotNull();
        assertThat(response.getExpiresAt()).isNotNull();
    }

    @Test
    void approve_shouldThrow_whenSellerDoesNotOwnItem() {
        when(sellerService.requireActiveSeller(99L)).thenReturn(seller(999L));
        ReturnRequest r = returnRequest(ReturnRequestStatus.REQUESTED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> returnService.approve(user(99L), 1L)).isInstanceOf(ReturnOwnershipException.class);
    }

    @Test
    void reject_shouldTransitionToRejected_whenRequested() {
        when(sellerService.requireActiveSeller(99L)).thenReturn(seller(5L));
        ReturnRequest r = returnRequest(ReturnRequestStatus.REQUESTED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = returnService.reject(user(99L), 1L);

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.REJECTED);
    }

    @Test
    void forceApprove_shouldNotCheckOwnership() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REQUESTED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReturnResponse response = returnService.forceApprove(1L);

        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.APPROVED);
        verify(sellerService, never()).requireActiveSeller(any());
    }

    // ---------- markItemReceived() ----------

    @Test
    void markItemReceived_shouldRestockAndApplySuccess_whenRefundSucceeds() {
        when(sellerService.requireActiveSeller(99L)).thenReturn(seller(5L));
        ReturnRequest r = returnRequest(ReturnRequestStatus.APPROVED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        OrderItemReturnInfo info = deliverableInfo(new BigDecimal("100.00"), 1, new BigDecimal("100.00"), BigDecimal.ZERO);
        when(orderService.getOrderItemForReturn(10L)).thenReturn(info);
        when(paymentService.refundPartial(eq(100L), eq(new BigDecimal("100.00")), any(), any(), eq(1L)))
                .thenReturn(true);

        returnService.markItemReceived(user(99L), 1L);

        verify(inventoryService).restock(50L, 1);
        verify(refundResultApplier).applyResult(1L, true);
    }

    @Test
    void markItemReceived_shouldApplyFailure_whenPaymentServiceReturnsFalse() {
        when(sellerService.requireActiveSeller(99L)).thenReturn(seller(5L));
        ReturnRequest r = returnRequest(ReturnRequestStatus.APPROVED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(returnRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        OrderItemReturnInfo info = deliverableInfo(new BigDecimal("100.00"), 1, new BigDecimal("100.00"), BigDecimal.ZERO);
        when(orderService.getOrderItemForReturn(10L)).thenReturn(info);
        when(paymentService.refundPartial(any(), any(), any(), any(), any())).thenReturn(false);

        returnService.markItemReceived(user(99L), 1L);

        verify(refundResultApplier).applyResult(1L, false);
    }

    @Test
    void markItemReceived_shouldThrow_whenNotApproved() {
        when(sellerService.requireActiveSeller(99L)).thenReturn(seller(5L));
        ReturnRequest r = returnRequest(ReturnRequestStatus.REQUESTED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> returnService.markItemReceived(user(99L), 1L))
                .isInstanceOf(ReturnStatusNotAllowedException.class);
        verify(inventoryService, never()).restock(any(), anyInt());
    }

    // ---------- retryRefund() ----------

    @Test
    void retryRefund_shouldThrow_whenNotRefundFailed() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REFUND_PENDING);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> returnService.retryRefund(1L)).isInstanceOf(ReturnStatusNotAllowedException.class);
        verify(refundResultApplier, never()).markPendingForRetry(any());
    }

    @Test
    void retryRefund_shouldMarkPendingAndCallPaymentService_whenRefundFailed() {
        ReturnRequest r = returnRequest(ReturnRequestStatus.REFUND_FAILED);
        when(returnRequestRepository.findById(1L)).thenReturn(Optional.of(r));
        when(paymentService.refundPartial(any(), any(), any(), any(), any())).thenReturn(true);

        returnService.retryRefund(1L);

        verify(refundResultApplier).markPendingForRetry(1L);
        verify(paymentService).refundPartial(eq(100L), eq(new BigDecimal("100.00")), any(), any(), eq(1L));
        verify(refundResultApplier).applyResult(1L, true);
    }

    // ---------- autoExpireApprovedReturns() ----------

    @Test
    void autoExpireApprovedReturns_shouldDelegateToMaintenanceProcessor_forEachId() {
        when(returnRequestRepository.findIdsByStatusAndExpiresAtBefore(
                eq(ReturnRequestStatus.APPROVED), any(), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));

        returnService.autoExpireApprovedReturns();

        verify(maintenanceProcessor, times(1)).expireOne(eq(1L), anyInt());
        verify(maintenanceProcessor, times(1)).expireOne(eq(2L), anyInt());
    }

    @Test
    void autoExpireApprovedReturns_shouldContinue_whenOneRequestFailsToExpire() {
        when(returnRequestRepository.findIdsByStatusAndExpiresAtBefore(
                eq(ReturnRequestStatus.APPROVED), any(), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(maintenanceProcessor).expireOne(eq(1L), anyInt());

        returnService.autoExpireApprovedReturns();

        verify(maintenanceProcessor).expireOne(eq(2L), anyInt());
    }
}
