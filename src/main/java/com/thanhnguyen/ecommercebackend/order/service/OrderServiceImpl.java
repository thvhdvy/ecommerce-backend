package com.thanhnguyen.ecommercebackend.order.service;

import com.thanhnguyen.ecommercebackend.cart.dto.CartItemResponse;
import com.thanhnguyen.ecommercebackend.cart.dto.CartResponse;
import com.thanhnguyen.ecommercebackend.cart.service.CartService;
import com.thanhnguyen.ecommercebackend.inventory.service.InventoryService;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemResponse;
import com.thanhnguyen.ecommercebackend.order.dto.OrderResponse;
import com.thanhnguyen.ecommercebackend.order.entity.Order;
import com.thanhnguyen.ecommercebackend.order.entity.OrderItem;
import com.thanhnguyen.ecommercebackend.order.entity.OrderItemStatus;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatusHistory;
import com.thanhnguyen.ecommercebackend.order.exception.EmptyCartException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderCancelNotAllowedException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderItemNotFoundException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderItemStatusNotAllowedException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderNotFoundException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderOwnershipException;
import com.thanhnguyen.ecommercebackend.order.repository.OrderItemRepository;
import com.thanhnguyen.ecommercebackend.order.repository.OrderRepository;
import com.thanhnguyen.ecommercebackend.order.repository.OrderStatusHistoryRepository;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import com.thanhnguyen.ecommercebackend.product.exception.NotASellerException;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.SellerStatus;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.exception.SellerLockedException;
import com.thanhnguyen.ecommercebackend.user.repository.SellerRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int PAYMENT_TIMEOUT_MINUTES = 15;
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED);
    // Admin force-cancel: superset cua CANCELLABLE_STATUSES + PACKED (customer khong tu huy duoc tu PACKED tro di).
    // SHIPPED/DELIVERED/COMPLETED khong cancel duoc (dung Return/Refund flow — v2, ngoai scope v1).
    private static final Set<OrderStatus> ADMIN_CANCELLABLE_STATUSES =
            Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, OrderStatus.PACKED);
    // Cancel tu cac status nay nghia la order da thanh toan thanh cong -> bat buoc trigger refund (design doc 0.6).
    private static final Set<OrderStatus> PAID_STATUSES = Set.of(OrderStatus.CONFIRMED, OrderStatus.PACKED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final SellerRepository sellerRepository;

    // PaymentServiceImpl phụ thuộc ngược lại OrderService (confirmPayment/markPaymentFailed) —
    // @Lazy ở chiều Order->Payment (chỉ dùng khi cancel order đã CONFIRMED) để phá vòng lặp khởi tạo bean.
    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            CartService cartService,
            InventoryService inventoryService,
            @Lazy PaymentService paymentService,
            SellerRepository sellerRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.sellerRepository = sellerRepository;
    }

    @Override
    @Transactional
    public OrderResponse checkout(User currentUser, CheckoutRequest request) {
        CartResponse cart = cartService.consumeCart(currentUser);
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        Order order = new Order();
        order.setCustomer(currentUser);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setShippingRecipientName(request.getShippingRecipientName());
        order.setShippingPhone(request.getShippingPhone());
        order.setShippingAddress(request.getShippingAddress());
        order.setShippingNote(request.getShippingNote());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItemResponse item : cart.getItems()) {
            inventoryService.reserveStock(item.getProductId(), item.getQuantity());

            order.getItems().add(new OrderItem(
                    order, item.getProductId(), item.getSellerId(),
                    item.getProductName(), item.getUnitPrice(), item.getQuantity()));
            totalAmount = totalAmount.add(item.getSubtotal());
        }
        order.setTotalAmount(totalAmount);

        Order saved = orderRepository.save(order);
        orderStatusHistoryRepository.save(
                new OrderStatusHistory(saved, null, OrderStatus.PENDING_PAYMENT, currentUser, null));

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> listMyOrders(User currentUser) {
        return orderRepository.findAllByCustomerIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(User currentUser, Long orderId) {
        Order order = resolveOwnedOrder(currentUser, orderId);
        return toResponse(order);
    }

    // Chi customer tu huy duoc tu PENDING_PAYMENT/CONFIRMED, nen "da thanh toan" o day chi co the la CONFIRMED.
    private static final Set<OrderStatus> CUSTOMER_PAID_STATUSES = Set.of(OrderStatus.CONFIRMED);

    @Override
    @Transactional
    public OrderResponse cancel(User currentUser, Long orderId) {
        Order order = resolveOwnedOrder(currentUser, orderId);
        return cancelOrder(order, currentUser, CANCELLABLE_STATUSES, CUSTOMER_PAID_STATUSES,
                null, "Customer cancelled order after payment");
    }

    @Override
    @Transactional
    public OrderResponse forceCancel(User admin, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return cancelOrder(order, admin, ADMIN_CANCELLABLE_STATUSES, PAID_STATUSES,
                "Admin force-cancel", "Admin force-cancelled order after payment");
    }

    /**
     * Logic dung chung cho cancel() (customer) va forceCancel() (admin) — chi khac nhau ve
     * tap status cho phep, tap status "da thanh toan" (can refund), va actor/reason ghi history.
     */
    private OrderResponse cancelOrder(
            Order order, User actor, Set<OrderStatus> allowedStatuses, Set<OrderStatus> paidStatuses,
            String historyReason, String refundReason) {
        if (!allowedStatuses.contains(order.getStatus())) {
            throw new OrderCancelNotAllowedException();
        }

        OrderStatus previousStatus = order.getStatus();

        // Cancel truoc payment: reserved > 0, tra lai available. Cancel sau payment (CONFIRMED/PACKED):
        // reserved da ve 0 tu buoc confirmPayment (commitReservedStock) — khong co gi de release,
        // va available khong tu tang lai (nhap kho lai thuoc luong return/refund v2, xem design doc dong 333).
        if (previousStatus == OrderStatus.PENDING_PAYMENT) {
            for (OrderItem item : order.getItems()) {
                inventoryService.releaseStock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = saveWithOptimisticLock(order);
        orderStatusHistoryRepository.save(
                new OrderStatusHistory(saved, previousStatus, OrderStatus.CANCELLED, actor, historyReason));

        if (paidStatuses.contains(previousStatus)) {
            paymentService.refund(order.getId(), refundReason, "127.0.0.1");
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void expirePendingPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PAYMENT_TIMEOUT_MINUTES);
        List<Order> expiredOrders = orderRepository.findAllByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff);

        for (Order order : expiredOrders) {
            for (OrderItem item : order.getItems()) {
                inventoryService.releaseStock(item.getProductId(), item.getQuantity());
            }

            order.setStatus(OrderStatus.PAYMENT_EXPIRED);
            orderRepository.save(order);
            orderStatusHistoryRepository.save(new OrderStatusHistory(
                    order, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_EXPIRED, null,
                    "Payment timeout after " + PAYMENT_TIMEOUT_MINUTES + " minutes"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toResponse(order);
    }

    @Override
    @Transactional
    public void reopenForPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            return;
        }

        order.setStatus(OrderStatus.PENDING_PAYMENT);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.PAYMENT_FAILED, OrderStatus.PENDING_PAYMENT, null, "Retry payment"));
    }

    @Override
    @Transactional
    public void confirmPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return; // idempotent guard — IPN trùng hoặc order đã được xử lý
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.commitReservedStock(item.getProductId(), item.getQuantity());
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, previousStatus, OrderStatus.CONFIRMED, null, "VNPay payment succeeded"));
    }

    @Override
    @Transactional
    public void markPaymentFailed(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return; // idempotent guard
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(
                new OrderStatusHistory(order, previousStatus, OrderStatus.PAYMENT_FAILED, null, reason));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> listSellerOrders(User currentUser) {
        Seller seller = resolveSeller(currentUser);
        List<OrderItem> items = orderItemRepository.findAllBySellerIdOrderByOrder_CreatedAtDesc(seller.getId());

        Map<Long, List<OrderItem>> itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId(), LinkedHashMap::new, Collectors.toList()));

        return itemsByOrderId.values().stream()
                .map(orderItems -> toResponse(orderItems.get(0).getOrder(), orderItems))
                .toList();
    }

    @Override
    @Transactional
    public OrderItemResponse packOrderItem(User currentUser, Long orderItemId) {
        Seller seller = resolveSeller(currentUser);

        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new OrderItemNotFoundException(orderItemId));

        if (!item.getSellerId().equals(seller.getId())) {
            throw new OrderOwnershipException();
        }

        Order order = item.getOrder();
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new OrderItemStatusNotAllowedException(
                    "Order is not in CONFIRMED status, current status: " + order.getStatus());
        }
        if (item.getItemStatus() == OrderItemStatus.PACKED) {
            throw new OrderItemStatusNotAllowedException("Order item is already PACKED");
        }

        item.setItemStatus(OrderItemStatus.PACKED);
        orderItemRepository.save(item);

        boolean allPacked = order.getItems().stream()
                .allMatch(i -> i.getItemStatus() == OrderItemStatus.PACKED);
        if (allPacked) {
            order.setStatus(OrderStatus.PACKED);
            orderRepository.save(order);
            orderStatusHistoryRepository.save(new OrderStatusHistory(
                    order, OrderStatus.CONFIRMED, OrderStatus.PACKED, currentUser, "All order items packed"));
        }

        return toItemResponse(item);
    }

    private static final int AUTO_COMPLETE_DAYS = 3;

    @Override
    @Transactional
    public void markShipped(Long orderId, User actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PACKED) {
            return; // idempotent guard — vd gán lại shipper khi order đã SHIPPED
        }

        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.PACKED, OrderStatus.SHIPPED, actor, "Shipper assigned"));
    }

    @Override
    @Transactional
    public void markDelivered(Long orderId, User actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.SHIPPED) {
            return; // idempotent guard
        }

        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.SHIPPED, OrderStatus.DELIVERED, actor, "Delivery succeeded"));
    }

    @Override
    @Transactional
    public void markFailedDeliveryAndRetry(Long orderId, User actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.SHIPPED) {
            return; // idempotent guard
        }

        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.SHIPPED, OrderStatus.FAILED_DELIVERY, actor, "Delivery attempt failed"));
        // Order status quay lai SHIPPED de retry (toi da 1 lan) — RETRY khong phai mot Order status rieng (xem design doc dong 116).
        // Entry nay la he qua tu dong cua business rule (retry), khong phai hanh dong truc tiep cua actor -> changed_by = null.
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.FAILED_DELIVERY, OrderStatus.SHIPPED, null, "Auto retry after failed delivery"));
        // order.status khong doi (van la SHIPPED) nen khong can save lai order.
    }

    @Override
    @Transactional
    public void markFailedDeliveryAndCancel(Long orderId, User actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.SHIPPED) {
            return; // idempotent guard
        }

        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.SHIPPED, OrderStatus.FAILED_DELIVERY, actor, "Delivery attempt failed (2nd time)"));

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        // Auto-cancel la he qua tu dong cua business rule (het quyen retry), khong phai hanh dong truc tiep cua actor -> changed_by = null.
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.FAILED_DELIVERY, OrderStatus.CANCELLED, null, "Auto-cancel after retry exhausted"));

        paymentService.refund(orderId, "Auto-cancel after 2nd failed delivery attempt", "127.0.0.1");
    }

    @Override
    @Transactional
    public void autoCompleteDeliveredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(AUTO_COMPLETE_DAYS);
        List<Order> deliveredOrders = orderRepository.findAllByStatusAndUpdatedAtBefore(OrderStatus.DELIVERED, cutoff);

        for (Order order : deliveredOrders) {
            order.setStatus(OrderStatus.COMPLETED);
            orderRepository.save(order);
            orderStatusHistoryRepository.save(new OrderStatusHistory(
                    order, OrderStatus.DELIVERED, OrderStatus.COMPLETED, null,
                    "Auto-completed after " + AUTO_COMPLETE_DAYS + " days"));
        }
    }

    private static final Set<OrderStatus> REVIEW_ELIGIBLE_STATUSES = Set.of(OrderStatus.DELIVERED, OrderStatus.COMPLETED);

    @Override
    @Transactional(readOnly = true)
    public Long findEligibleOrderIdForReview(User customer, Long productId) {
        return orderItemRepository
                .findFirstByProductIdAndOrder_CustomerIdAndOrder_StatusInOrderByOrder_CreatedAtDesc(
                        productId, customer.getId(), REVIEW_ELIGIBLE_STATUSES)
                .map(item -> item.getOrder().getId())
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> listAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private Seller resolveSeller(User currentUser) {
        Seller seller = sellerRepository.findByUserId(currentUser.getId())
                .orElseThrow(NotASellerException::new);
        if (seller.getStatus() == SellerStatus.LOCKED) {
            throw new SellerLockedException();
        }
        return seller;
    }

    /**
     * saveAndFlush (khong phai save thuong) de bat ObjectOptimisticLockingFailureException ngay tai day,
     * truoc khi tiep tuc goi paymentService.refund() — phong 2 request cancel/forceCancel dong thoi tren
     * cung 1 order cung doc duoc status hop le truoc khi ben kia commit, dan toi refund 2 lan (xem Order.version).
     */
    private Order saveWithOptimisticLock(Order order) {
        try {
            return orderRepository.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OrderCancelNotAllowedException();
        }
    }

    private Order resolveOwnedOrder(User currentUser, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getCustomer().getId().equals(currentUser.getId())) {
            throw new OrderOwnershipException();
        }

        return order;
    }

    private OrderResponse toResponse(Order order) {
        return toResponse(order, order.getItems());
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getShippingRecipientName(),
                order.getShippingPhone(),
                order.getShippingAddress(),
                order.getShippingNote(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getSellerId(),
                item.getProductNameSnapshot(),
                item.getUnitPriceSnapshot(),
                item.getQuantity(),
                item.getItemStatus());
    }
}
