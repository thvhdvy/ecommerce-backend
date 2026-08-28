package com.thanhnguyen.ecommercebackend.shipping.service;

import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.shipping.entity.Delivery;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryFailureReason;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatus;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryNotAllowedException;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryNotFoundException;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryOwnershipException;
import com.thanhnguyen.ecommercebackend.shipping.exception.NotAShipperException;
import com.thanhnguyen.ecommercebackend.shipping.repository.DeliveryRepository;
import com.thanhnguyen.ecommercebackend.shipping.repository.DeliveryStatusHistoryRepository;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserRole;
import com.thanhnguyen.ecommercebackend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingServiceImplTest {

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;
    @Mock
    private UserService userService;
    @Mock
    private OrderService orderService;

    private ShippingServiceImpl shippingService;

    private static final Long ORDER_ID = 100L;
    private static final Long SELLER_ID = 1L;
    private static final Long SHIPPER_ID = 5L;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingServiceImpl(deliveryRepository, deliveryStatusHistoryRepository, userService, orderService);
    }

    private User shipper(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(UserRole.SHIPPER);
        user.setFullName("Shipper " + id);
        return user;
    }

    private Delivery delivery(DeliveryStatus status, User shipper, int retryCount) {
        Delivery delivery = new Delivery(ORDER_ID, SELLER_ID, shipper);
        delivery.setStatus(status);
        delivery.setRetryCount(retryCount);
        return delivery;
    }

    // ---------- assignShipper() ----------

    @Test
    void assignShipper_shouldThrow_whenSellerItemsNotFullyPacked() {
        when(orderService.areSellerItemsPacked(ORDER_ID, SELLER_ID)).thenReturn(false);

        assertThatThrownBy(() -> shippingService.assignShipper(ORDER_ID, SELLER_ID, SHIPPER_ID, shipper(99L)))
                .isInstanceOf(DeliveryNotAllowedException.class);
        verify(userService, never()).getEntityById(any());
    }

    @Test
    void assignShipper_shouldThrow_whenTargetUserNotShipper() {
        when(orderService.areSellerItemsPacked(ORDER_ID, SELLER_ID)).thenReturn(true);
        User notShipper = new User();
        notShipper.setId(SHIPPER_ID);
        notShipper.setRole(UserRole.CUSTOMER);
        when(userService.getEntityById(SHIPPER_ID)).thenReturn(notShipper);

        assertThatThrownBy(() -> shippingService.assignShipper(ORDER_ID, SELLER_ID, SHIPPER_ID, shipper(99L)))
                .isInstanceOf(NotAShipperException.class);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void assignShipper_shouldCreateNewDelivery_whenNoneExistsYet() {
        when(orderService.areSellerItemsPacked(ORDER_ID, SELLER_ID)).thenReturn(true);
        User shipper = shipper(SHIPPER_ID);
        when(userService.getEntityById(SHIPPER_ID)).thenReturn(shipper);
        when(deliveryRepository.findByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(Optional.empty());

        DeliveryResponse response = shippingService.assignShipper(ORDER_ID, SELLER_ID, SHIPPER_ID, shipper(99L));

        assertThat(response.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        verify(deliveryRepository).save(any(Delivery.class));
        verify(deliveryStatusHistoryRepository).save(argThat(h ->
                h.getFromStatus() == null && h.getToStatus() == DeliveryStatus.ASSIGNED
                        && "Shipper assigned".equals(h.getReason())));
        verify(orderService).recomputeAggregateStatus(ORDER_ID);
    }

    @Test
    void assignShipper_shouldReassignExistingDelivery_andRecordPreviousStatusInHistory() {
        when(orderService.areSellerItemsPacked(ORDER_ID, SELLER_ID)).thenReturn(true);
        User newShipper = shipper(SHIPPER_ID);
        when(userService.getEntityById(SHIPPER_ID)).thenReturn(newShipper);
        Delivery existing = delivery(DeliveryStatus.IN_TRANSIT, shipper(77L), 0);
        when(deliveryRepository.findByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(Optional.of(existing));

        shippingService.assignShipper(ORDER_ID, SELLER_ID, SHIPPER_ID, shipper(99L));

        assertThat(existing.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(existing.getShipper()).isEqualTo(newShipper);
        verify(deliveryStatusHistoryRepository).save(argThat(h ->
                h.getFromStatus() == DeliveryStatus.IN_TRANSIT && h.getToStatus() == DeliveryStatus.ASSIGNED
                        && "Shipper reassigned".equals(h.getReason())));
    }

    // ---------- updateDeliveryStatus() ----------

    @Test
    void updateDeliveryStatus_shouldThrow_whenDeliveryNotFound() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingService.updateDeliveryStatus(
                shipper(SHIPPER_ID), 1L, new DeliveryStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, null)))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    void updateDeliveryStatus_shouldThrow_whenCurrentUserIsNotTheAssignedShipper() {
        Delivery d = delivery(DeliveryStatus.ASSIGNED, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> shippingService.updateDeliveryStatus(
                shipper(999L), 1L, new DeliveryStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, null)))
                .isInstanceOf(DeliveryOwnershipException.class);
    }

    @Test
    void updateDeliveryStatus_shouldThrow_whenDeliveryNotInProgress() {
        Delivery d = delivery(DeliveryStatus.DELIVERED, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> shippingService.updateDeliveryStatus(
                shipper(SHIPPER_ID), 1L, new DeliveryStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, null)))
                .isInstanceOf(DeliveryNotAllowedException.class);
    }

    @Test
    void updateDeliveryStatus_shouldThrow_whenTargetIsAssigned() {
        Delivery d = delivery(DeliveryStatus.IN_TRANSIT, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> shippingService.updateDeliveryStatus(
                shipper(SHIPPER_ID), 1L, new DeliveryStatusUpdateRequest(DeliveryStatus.ASSIGNED, null)))
                .isInstanceOf(DeliveryNotAllowedException.class);
    }

    @Test
    void updateDeliveryStatus_shouldMoveToInTransit_andSetPickedUpAt() {
        Delivery d = delivery(DeliveryStatus.ASSIGNED, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(d));

        shippingService.updateDeliveryStatus(
                shipper(SHIPPER_ID), 1L, new DeliveryStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, null));

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(d.getPickedUpAt()).isNotNull();
        verify(orderService, never()).recomputeAggregateStatus(any());
    }

    @Test
    void updateDeliveryStatus_shouldMoveToDelivered_andRecomputeAggregate() {
        Delivery d = delivery(DeliveryStatus.IN_TRANSIT, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(d));

        shippingService.updateDeliveryStatus(
                shipper(SHIPPER_ID), 1L, new DeliveryStatusUpdateRequest(DeliveryStatus.DELIVERED, null));

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(d.getDeliveredAt()).isNotNull();
        verify(orderService).recomputeAggregateStatus(ORDER_ID);
    }

    @Test
    void updateDeliveryStatus_shouldThrow_whenFailedWithoutFailureReason() {
        Delivery d = delivery(DeliveryStatus.ASSIGNED, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> shippingService.updateDeliveryStatus(
                shipper(SHIPPER_ID), 1L, new DeliveryStatusUpdateRequest(DeliveryStatus.FAILED, null)))
                .isInstanceOf(DeliveryNotAllowedException.class);
        verify(orderService, never()).markFailedDeliveryAndCancel(any(), any());
    }

    @Test
    void updateDeliveryStatus_firstFailure_shouldAutoRetry_andKeepAssignable() {
        Delivery d = delivery(DeliveryStatus.IN_TRANSIT, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(d));

        shippingService.updateDeliveryStatus(shipper(SHIPPER_ID), 1L,
                new DeliveryStatusUpdateRequest(DeliveryStatus.FAILED, DeliveryFailureReason.CUSTOMER_UNREACHABLE));

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(d.getRetryCount()).isEqualTo(1);
        // 2 dong history: FAILED (ly do) + auto retry FAILED->ASSIGNED (changed_by = null)
        verify(deliveryStatusHistoryRepository, times(2)).save(any());
        verify(deliveryStatusHistoryRepository).save(argThat(h ->
                h.getFromStatus() == DeliveryStatus.FAILED && h.getToStatus() == DeliveryStatus.ASSIGNED
                        && h.getChangedBy() == null));
        verify(orderService).recomputeAggregateStatus(ORDER_ID);
        verify(orderService, never()).markFailedDeliveryAndCancel(any(), any());
    }

    @Test
    void updateDeliveryStatus_secondFailure_shouldBeTerminal_andTriggerCancel() {
        Delivery d = delivery(DeliveryStatus.IN_TRANSIT, shipper(SHIPPER_ID), 1);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(d));

        shippingService.updateDeliveryStatus(shipper(SHIPPER_ID), 1L,
                new DeliveryStatusUpdateRequest(DeliveryStatus.FAILED, DeliveryFailureReason.ADDRESS_ISSUE));

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(d.getRetryCount()).isEqualTo(1);
        verify(orderService).markFailedDeliveryAndCancel(ORDER_ID, SELLER_ID);
        verify(orderService, never()).recomputeAggregateStatus(any());
    }

    // ---------- confirmDeliveredManually() ----------

    @Test
    void confirmDeliveredManually_shouldThrow_whenDeliveryNotFound() {
        when(deliveryRepository.findByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingService.confirmDeliveredManually(shipper(1L), ORDER_ID, SELLER_ID))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    @Test
    void confirmDeliveredManually_shouldThrow_whenDeliveryAlreadyDelivered() {
        Delivery d = delivery(DeliveryStatus.DELIVERED, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> shippingService.confirmDeliveredManually(shipper(1L), ORDER_ID, SELLER_ID))
                .isInstanceOf(DeliveryNotAllowedException.class);
    }

    @Test
    void confirmDeliveredManually_shouldMoveToDelivered_andRecomputeAggregate() {
        Delivery d = delivery(DeliveryStatus.IN_TRANSIT, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(Optional.of(d));

        shippingService.confirmDeliveredManually(shipper(1L), ORDER_ID, SELLER_ID);

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        verify(orderService).recomputeAggregateStatus(ORDER_ID);
    }

    // ---------- isDeliveredForSeller() ----------

    @Test
    void isDeliveredForSeller_shouldReturnFalse_whenNoDeliveryExists() {
        when(deliveryRepository.findByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(Optional.empty());

        assertThat(shippingService.isDeliveredForSeller(ORDER_ID, SELLER_ID)).isFalse();
    }

    @Test
    void isDeliveredForSeller_shouldReturnTrue_onlyWhenStatusIsDelivered() {
        Delivery d = delivery(DeliveryStatus.DELIVERED, shipper(SHIPPER_ID), 0);
        when(deliveryRepository.findByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(Optional.of(d));

        assertThat(shippingService.isDeliveredForSeller(ORDER_ID, SELLER_ID)).isTrue();
    }
}
