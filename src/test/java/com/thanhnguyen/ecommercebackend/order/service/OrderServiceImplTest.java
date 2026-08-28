package com.thanhnguyen.ecommercebackend.order.service;

import com.thanhnguyen.ecommercebackend.cart.service.CartService;
import com.thanhnguyen.ecommercebackend.coupon.service.CouponService;
import com.thanhnguyen.ecommercebackend.inventory.service.InventoryService;
import com.thanhnguyen.ecommercebackend.notification.event.NotificationType;
import com.thanhnguyen.ecommercebackend.notification.event.OrderNotificationEvent;
import com.thanhnguyen.ecommercebackend.order.entity.Order;
import com.thanhnguyen.ecommercebackend.order.entity.OrderItem;
import com.thanhnguyen.ecommercebackend.order.entity.OrderItemStatus;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.order.repository.OrderItemRepository;
import com.thanhnguyen.ecommercebackend.order.repository.OrderRepository;
import com.thanhnguyen.ecommercebackend.order.repository.OrderStatusHistoryRepository;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatus;
import com.thanhnguyen.ecommercebackend.shipping.service.ShippingService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cover rieng recomputeAggregateStatus() — thuat toan aggregate-min qua nhieu seller (design doc v2
 * muc 10.4). markFailedDeliveryAndCancel() de lai lam task rieng.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

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
    @Mock
    private CouponService couponService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ShippingService shippingService;

    private OrderServiceImpl orderService;

    private static final Long ORDER_ID = 100L;
    private static final Long SELLER_A = 1L;
    private static final Long SELLER_B = 2L;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderRepository, orderItemRepository, orderStatusHistoryRepository,
                cartService, inventoryService, paymentService, sellerService,
                maintenanceProcessor, couponService, eventPublisher, shippingService);
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(status);
        return order;
    }

    private OrderItem item(Order order, Long sellerId, OrderItemStatus itemStatus) {
        OrderItem item = new OrderItem(order, 10L, sellerId, "Product", new BigDecimal("50.00"), 1);
        item.setItemStatus(itemStatus);
        order.getItems().add(item);
        return item;
    }

    @Test
    void recomputeAggregateStatus_shouldStayAtLowerRank_whenOneSellerStillInTransit() {
        Order order = order(OrderStatus.PACKED);
        item(order, SELLER_A, OrderItemStatus.PACKED);
        item(order, SELLER_B, OrderItemStatus.PACKED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shippingService.getDeliveryStatusesBySeller(ORDER_ID)).thenReturn(Map.of(
                SELLER_A, DeliveryStatus.DELIVERED,
                SELLER_B, DeliveryStatus.IN_TRANSIT));

        orderService.recomputeAggregateStatus(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(eventPublisher).publishEvent(new OrderNotificationEvent(NotificationType.ORDER_SHIPPED, ORDER_ID));
    }

    @Test
    void recomputeAggregateStatus_shouldStayConfirmed_whenOneSellerNotFullyPacked() {
        Order order = order(OrderStatus.CONFIRMED);
        item(order, SELLER_A, OrderItemStatus.PACKED);
        item(order, SELLER_B, OrderItemStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shippingService.getDeliveryStatusesBySeller(ORDER_ID)).thenReturn(Map.of(
                SELLER_A, DeliveryStatus.DELIVERED));

        orderService.recomputeAggregateStatus(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recomputeAggregateStatus_shouldTreatFailedWithRetryLeft_asShippedRank() {
        Order order = order(OrderStatus.PACKED);
        item(order, SELLER_A, OrderItemStatus.PACKED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shippingService.getDeliveryStatusesBySeller(ORDER_ID)).thenReturn(Map.of(
                SELLER_A, DeliveryStatus.FAILED));

        orderService.recomputeAggregateStatus(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(eventPublisher).publishEvent(new OrderNotificationEvent(NotificationType.ORDER_SHIPPED, ORDER_ID));
    }

    @Test
    void recomputeAggregateStatus_shouldExcludeCancelledSeller_fromRankCalculation() {
        Order order = order(OrderStatus.SHIPPED);
        item(order, SELLER_A, OrderItemStatus.CANCELLED);
        item(order, SELLER_B, OrderItemStatus.PACKED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shippingService.getDeliveryStatusesBySeller(ORDER_ID)).thenReturn(Map.of(
                SELLER_B, DeliveryStatus.DELIVERED));

        orderService.recomputeAggregateStatus(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(eventPublisher).publishEvent(new OrderNotificationEvent(NotificationType.ORDER_DELIVERED, ORDER_ID));
    }

    @Test
    void recomputeAggregateStatus_shouldNoOp_whenComputedRankMatchesCurrentStatus() {
        Order order = order(OrderStatus.SHIPPED);
        item(order, SELLER_A, OrderItemStatus.PACKED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shippingService.getDeliveryStatusesBySeller(ORDER_ID)).thenReturn(Map.of(
                SELLER_A, DeliveryStatus.IN_TRANSIT));

        orderService.recomputeAggregateStatus(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recomputeAggregateStatus_shouldNotPublishEvent_whenNewRankIsConfirmedOrPacked() {
        Order order = order(OrderStatus.CONFIRMED);
        item(order, SELLER_A, OrderItemStatus.PACKED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shippingService.getDeliveryStatusesBySeller(ORDER_ID)).thenReturn(Map.of());

        orderService.recomputeAggregateStatus(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PACKED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recomputeAggregateStatus_shouldSkipEntirely_whenOrderStatusNotAggregateTracked() {
        Order order = order(OrderStatus.CANCELLED);
        item(order, SELLER_A, OrderItemStatus.PACKED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.recomputeAggregateStatus(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(shippingService, never()).getDeliveryStatusesBySeller(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void markFailedDeliveryAndCancel_shouldNoOp_whenSellerAlreadyCancelled() {
        Order order = order(OrderStatus.SHIPPED);
        item(order, SELLER_A, OrderItemStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.markFailedDeliveryAndCancel(ORDER_ID, SELLER_A);

        verify(orderItemRepository, never()).saveAll(any());
        verify(orderRepository, never()).save(any());
        verify(paymentService, never()).refundPartial(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void markFailedDeliveryAndCancel_shouldCancelOnlyThatSeller_andRecomputeAggregate_whenOtherSellerStillActive() {
        Order order = order(OrderStatus.SHIPPED);
        order.setTotalAmount(new BigDecimal("100.00"));
        OrderItem sellerAItem = item(order, SELLER_A, OrderItemStatus.PACKED);
        item(order, SELLER_B, OrderItemStatus.PACKED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shippingService.getDeliveryStatusesBySeller(ORDER_ID)).thenReturn(Map.of(
                SELLER_B, DeliveryStatus.DELIVERED));
        when(paymentService.refundPartial(eq(ORDER_ID), eq(new BigDecimal("50.00")), any(), any(), eq(null)))
                .thenReturn(true);

        orderService.markFailedDeliveryAndCancel(ORDER_ID, SELLER_A);

        assertThat(sellerAItem.getItemStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        verify(orderItemRepository).saveAll(java.util.List.of(sellerAItem));
        // Seller A bi loai khoi tap tinh aggregate-min (da CANCELLED) -> chi con seller B (DELIVERED) quyet dinh rank.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(eventPublisher).publishEvent(new OrderNotificationEvent(NotificationType.ORDER_DELIVERED, ORDER_ID));
        verify(paymentService).refundPartial(ORDER_ID, new BigDecimal("50.00"),
                "Seller #" + SELLER_A + " delivery retry exhausted", "127.0.0.1", null);
    }

    @Test
    void markFailedDeliveryAndCancel_shouldCancelWholeOrder_whenLastActiveSellerCancelled() {
        Order order = order(OrderStatus.SHIPPED);
        order.setTotalAmount(new BigDecimal("100.00"));
        OrderItem sellerAItem = item(order, SELLER_A, OrderItemStatus.PACKED);
        item(order, SELLER_B, OrderItemStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentService.refundPartial(any(), any(), any(), any(), any())).thenReturn(true);

        orderService.markFailedDeliveryAndCancel(ORDER_ID, SELLER_A);

        assertThat(sellerAItem.getItemStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(orderStatusHistoryRepository).save(any());
        verify(eventPublisher).publishEvent(new OrderNotificationEvent(NotificationType.ORDER_CANCELLED, ORDER_ID));
        verify(shippingService, never()).getDeliveryStatusesBySeller(any());
        verify(paymentService).refundPartial(eq(ORDER_ID), eq(new BigDecimal("50.00")), any(), any(), eq(null));
    }

    // ---------- forceCancelSellerItems() ----------

    @Test
    void forceCancelSellerItems_shouldWriteHistoryRow_evenWhenAggregateRankUnchanged() {
        // Gap tim thay khi review cheo cac module (design doc v2 muc 10.5): huy 1 phan khong doi rank
        // aggregate (seller con lai van CONFIRMED) truoc day khong de lai vet nao trong history vi
        // recomputeAggregateStatus() la no-op khi rank trung. Gio phai luon ghi 1 dong danh dau hanh
        // dong cua actor, du from=to cung 1 status.
        Order order = order(OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("100.00"));
        OrderItem sellerAItem = item(order, SELLER_A, OrderItemStatus.PENDING);
        item(order, SELLER_B, OrderItemStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(shippingService.getDeliveryStatusesBySeller(ORDER_ID)).thenReturn(Map.of());
        User admin = new User();
        admin.setId(999L);

        orderService.forceCancelSellerItems(admin, ORDER_ID, SELLER_A);

        assertThat(sellerAItem.getItemStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED); // rank khong doi
        verify(orderStatusHistoryRepository).save(argThat(h ->
                h.getFromStatus() == OrderStatus.CONFIRMED && h.getToStatus() == OrderStatus.CONFIRMED
                        && h.getChangedBy() == admin
                        && h.getReason().equals("Seller #" + SELLER_A + " items cancelled")));
    }

    // ---------- getOrderItemsForPayout() ----------

    @Test
    void getOrderItemsForPayout_shouldExcludeCancelledItems() {
        // Bug tim thay khi review cheo Payout x Shipment-split (design doc v2 muc 10.5): seller bi
        // huy per-seller khong duoc tinh EARNED cho phan hang chua tung giao ma khach da duoc hoan.
        Order order = order(OrderStatus.COMPLETED);
        OrderItem cancelledItem = item(order, SELLER_A, OrderItemStatus.CANCELLED);
        OrderItem activeItem = item(order, SELLER_B, OrderItemStatus.PACKED);
        when(orderItemRepository.findAllByOrderId(ORDER_ID)).thenReturn(java.util.List.of(cancelledItem, activeItem));

        var result = orderService.getOrderItemsForPayout(ORDER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSellerId()).isEqualTo(SELLER_B);
    }
}
