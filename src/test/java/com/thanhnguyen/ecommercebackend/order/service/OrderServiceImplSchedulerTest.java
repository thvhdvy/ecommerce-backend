package com.thanhnguyen.ecommercebackend.order.service;

import com.thanhnguyen.ecommercebackend.cart.service.CartService;
import com.thanhnguyen.ecommercebackend.inventory.service.InventoryService;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.order.repository.OrderItemRepository;
import com.thanhnguyen.ecommercebackend.order.repository.OrderRepository;
import com.thanhnguyen.ecommercebackend.order.repository.OrderStatusHistoryRepository;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test rieng cho 2 scheduled job: dam bao 1 order loi khong chan ca batch (moi order duoc xu ly
 * doc lap qua OrderMaintenanceProcessor, loi chi log va di tiep).
 */
// LENIENT: doThrow stub theo dung 1 orderId (eq(2L)) — strict-stubs se coi cac call cung method
// voi orderId khac la PotentialStubbingProblem va throw nham vao try/catch cua batch loop.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplSchedulerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private CartService cartService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private SellerService sellerService;
    @Mock
    private OrderMaintenanceProcessor maintenanceProcessor;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void expirePendingPayments_shouldContinueBatch_whenOneOrderFails() {
        when(orderRepository.findIdsByStatusAndCreatedAtBefore(
                eq(OrderStatus.PENDING_PAYMENT), any(), any()))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("inventory du lieu lech"))
                .when(maintenanceProcessor).expireOne(eq(2L), anyInt());

        orderService.expirePendingPayments();

        // Order 2 loi nhung order 3 van duoc xu ly — khong chan batch
        verify(maintenanceProcessor).expireOne(eq(1L), anyInt());
        verify(maintenanceProcessor).expireOne(eq(2L), anyInt());
        verify(maintenanceProcessor).expireOne(eq(3L), anyInt());
    }

    @Test
    void autoCompleteDeliveredOrders_shouldContinueBatch_whenOneOrderFails() {
        when(orderRepository.findIdsByStatusAndUpdatedAtBefore(
                eq(OrderStatus.DELIVERED), any(), any()))
                .thenReturn(List.of(10L, 11L));
        doThrow(new RuntimeException("db loi tam thoi"))
                .when(maintenanceProcessor).completeOne(eq(10L), anyInt());

        orderService.autoCompleteDeliveredOrders();

        verify(maintenanceProcessor).completeOne(eq(10L), anyInt());
        verify(maintenanceProcessor).completeOne(eq(11L), anyInt());
    }
}
