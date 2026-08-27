package com.thanhnguyen.ecommercebackend.order.service;

import com.thanhnguyen.ecommercebackend.cart.dto.CartItemResponse;
import com.thanhnguyen.ecommercebackend.cart.dto.CartResponse;
import com.thanhnguyen.ecommercebackend.cart.service.CartService;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.coupon.service.CouponService;
import com.thanhnguyen.ecommercebackend.inventory.service.InventoryService;
import com.thanhnguyen.ecommercebackend.notification.event.NotificationType;
import com.thanhnguyen.ecommercebackend.notification.event.OrderNotificationEvent;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemPayoutInfo;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemResponse;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemReturnInfo;
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
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatus;
import com.thanhnguyen.ecommercebackend.shipping.service.ShippingService;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final int PAYMENT_TIMEOUT_MINUTES = 15;
    // Gioi han so order xu ly moi lan scheduled job chay — backlog lon (vd sau downtime) duoc tieu
    // dan qua nhieu lan chay thay vi 1 transaction/1 lan quet khong lo.
    private static final int MAINTENANCE_BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final SellerService sellerService;
    private final OrderMaintenanceProcessor maintenanceProcessor;
    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;
    private final ShippingService shippingService;

    // PaymentServiceImpl phụ thuộc ngược lại OrderService (confirmPayment/markPaymentFailed) —
    // @Lazy ở chiều Order->Payment (chỉ dùng khi cancel order đã CONFIRMED) để phá vòng lặp khởi tạo bean.
    // Tương tự, ShippingServiceImpl phụ thuộc OrderService (areSellerItemsPacked/recomputeAggregateStatus)
    // nên chiều ngược lại Order->Shipping (đọc delivery status để tính aggregate-min, mục 10.4) cũng
    // phải @Lazy để phá vòng lặp khởi tạo bean.
    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            CartService cartService,
            InventoryService inventoryService,
            @Lazy PaymentService paymentService,
            SellerService sellerService,
            OrderMaintenanceProcessor maintenanceProcessor,
            CouponService couponService,
            ApplicationEventPublisher eventPublisher,
            @Lazy ShippingService shippingService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.sellerService = sellerService;
        this.maintenanceProcessor = maintenanceProcessor;
        this.couponService = couponService;
        this.eventPublisher = eventPublisher;
        this.shippingService = shippingService;
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

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItemResponse item : cart.getItems()) {
            inventoryService.reserveStock(item.getProductId(), item.getQuantity());

            order.getItems().add(new OrderItem(
                    order, item.getProductId(), item.getSellerId(),
                    item.getProductName(), item.getUnitPrice(), item.getQuantity()));
            subtotal = subtotal.add(item.getSubtotal());
        }
        order.setTotalAmount(subtotal);

        // Save truoc de lay order.id that (IDENTITY) — coupon_redemptions.order_id can 1 id da ton
        // tai (design doc v2 muc 6.3). Neu co coupon, save lai lan 2 sau khi ap discount.
        Order saved = orderRepository.save(order);

        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            BigDecimal discountAmount = couponService.reserve(
                    currentUser, request.getCouponCode(), saved.getId(), subtotal);
            saved.setCouponCode(request.getCouponCode());
            saved.setDiscountAmount(discountAmount);
            saved.setTotalAmount(subtotal.subtract(discountAmount));
            saved = orderRepository.save(saved);
        }

        orderStatusHistoryRepository.save(
                new OrderStatusHistory(saved, null, OrderStatus.PENDING_PAYMENT, currentUser, null));

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMyOrders(User currentUser, Pageable pageable) {
        return PageResponse.from(
                orderRepository.findAllByCustomerId(currentUser.getId(), pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(User currentUser, Long orderId) {
        Order order = resolveOwnedOrder(currentUser, orderId);
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancel(User currentUser, Long orderId) {
        Order order = resolveOwnedOrder(currentUser, orderId);
        return cancelBeforePayment(order, currentUser, "Customer cancelled order before payment");
    }

    @Override
    @Transactional
    public OrderResponse forceCancel(User admin, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return cancelBeforePayment(order, admin, "Admin force-cancelled order before payment");
    }

    /**
     * Huy toan bo order — chi con ap dung khi PENDING_PAYMENT (design doc v2 muc 10.5.1): tu khi
     * CONFIRMED tro di, huy phai qua cancelSellerItems/forceCancelSellerItems (per-seller), vi tu
     * luc do cac seller da co the o nhung tien do khac nhau (pack/delivery doc lap).
     */
    private OrderResponse cancelBeforePayment(Order order, User actor, String historyReason) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new OrderCancelNotAllowedException();
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseStock(item.getProductId(), item.getQuantity());
        }
        couponService.release(order.getId());

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = saveWithOptimisticLock(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                saved, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, actor, historyReason));
        eventPublisher.publishEvent(new OrderNotificationEvent(NotificationType.ORDER_CANCELLED, saved.getId()));

        return toResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancelSellerItems(User currentUser, Long orderId, Long sellerId) {
        Order order = resolveOwnedOrder(currentUser, orderId);
        return cancelSellerItems(order, sellerId, currentUser, false);
    }

    @Override
    @Transactional
    public OrderResponse forceCancelSellerItems(User admin, Long orderId, Long sellerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return cancelSellerItems(order, sellerId, admin, true);
    }

    /**
     * Huy toan bo phan hang cua 1 seller trong order (design doc v2 muc 10.5). Gate: customer chi
     * duoc huy khi seller do chua PACKED xong; admin duoc them quyen huy ca khi da PACKED, nhung
     * khong ai huy duoc khi seller do da co delivery (tuong duong SHIPPED tro len — dung Return flow).
     * Khong giai phong ton kho (giong rule huy sau CONFIRMED o v1). Refund mot phan qua
     * PaymentService.refundPartial() da co san cho Return module, prorate giong het cong thuc Return.
     */
    private OrderResponse cancelSellerItems(Order order, Long sellerId, User actor, boolean isAdmin) {
        // SHIPPED/DELIVERED khong can chan rieng: neu order o hang do, MOI seller (ke ca sellerId nay)
        // da co delivery roi (dinh nghia aggregate-min - muc 10.4), nen check delivery ben duoi da tu chan.
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PACKED) {
            throw new OrderCancelNotAllowedException();
        }

        List<OrderItem> sellerItems = order.getItems().stream()
                .filter(item -> item.getSellerId().equals(sellerId))
                .toList();
        if (sellerItems.isEmpty() || sellerItems.stream().anyMatch(i -> i.getItemStatus() == OrderItemStatus.CANCELLED)) {
            throw new OrderCancelNotAllowedException();
        }
        if (shippingService.getDeliveryStatusesBySeller(order.getId()).containsKey(sellerId)) {
            throw new OrderCancelNotAllowedException();
        }
        boolean allPacked = sellerItems.stream().allMatch(i -> i.getItemStatus() == OrderItemStatus.PACKED);
        if (allPacked && !isAdmin) {
            throw new OrderCancelNotAllowedException();
        }

        BigDecimal refundAmount = proratedRefundAmount(sellerItems, order);

        for (OrderItem item : sellerItems) {
            item.setItemStatus(OrderItemStatus.CANCELLED);
        }
        orderItemRepository.saveAll(sellerItems);

        boolean allSellersCancelled = order.getItems().stream()
                .allMatch(item -> item.getItemStatus() == OrderItemStatus.CANCELLED);
        if (allSellersCancelled) {
            OrderStatus previous = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            orderStatusHistoryRepository.save(new OrderStatusHistory(
                    order, previous, OrderStatus.CANCELLED, actor, "All sellers cancelled"));
            eventPublisher.publishEvent(new OrderNotificationEvent(NotificationType.ORDER_CANCELLED, order.getId()));
        } else {
            recomputeAggregateStatus(order);
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundPartialAfterCommit(order.getId(), refundAmount, "Seller #" + sellerId + " items cancelled");
        }

        return toResponse(order);
    }

    // Cung cong thuc prorate voi Return module (ReturnServiceImpl.proratedRefundAmount, design doc
    // v2 muc 7.3) — tong gross cua cac item thuoc seller, nhan ty le orderTotalAmount/(total+discount).
    private BigDecimal proratedRefundAmount(List<OrderItem> sellerItems, Order order) {
        BigDecimal grossAmount = sellerItems.stream()
                .map(item -> item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPlusDiscount = order.getTotalAmount().add(order.getDiscountAmount());
        if (totalPlusDiscount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discountRatio = order.getTotalAmount()
                .divide(totalPlusDiscount, 6, java.math.RoundingMode.HALF_UP);
        return grossAmount.multiply(discountRatio).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // VNPay refund goi mang ra ngoai — khong duoc chay ben trong transaction dang giu row lock cua
    // order (UPDATE status + optimistic lock vua chay o tren). Chi trigger sau khi transaction hien
    // tai commit xong, de connection/lock duoc giai phong truoc khi cho VNPay tra ve.
    private void refundAfterCommit(Long orderId, String refundReason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    paymentService.refund(orderId, refundReason, "127.0.0.1");
                }
            });
        } else {
            paymentService.refund(orderId, refundReason, "127.0.0.1");
        }
    }

    // returnRequestId=null: refund khong xuat phat tu 1 ReturnRequest (cot refunds.return_request_id
    // von da nullable — design doc v2 muc 10.5.1). Refund that bai di qua hang doi admin retry san
    // co (listFailedRefunds/resolveRefundManually), khong can co che retry rieng.
    private void refundPartialAfterCommit(Long orderId, BigDecimal amount, String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    paymentService.refundPartial(orderId, amount, reason, "127.0.0.1", null);
                }
            });
        } else {
            paymentService.refundPartial(orderId, amount, reason, "127.0.0.1", null);
        }
    }

    // Khong @Transactional o day (co chu dich): moi order duoc xu ly trong transaction RIENG boi
    // maintenanceProcessor — 1 order loi chi rollback + log rieng order do, khong chan ca batch
    // (truoc day 1 order "doc" dau danh sach se lam worker rollback het va ket vinh vien tai do).
    @Override
    public void expirePendingPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PAYMENT_TIMEOUT_MINUTES);
        List<Long> orderIds = orderRepository.findIdsByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, cutoff, PageRequest.of(0, MAINTENANCE_BATCH_SIZE));

        for (Long orderId : orderIds) {
            try {
                maintenanceProcessor.expireOne(orderId, PAYMENT_TIMEOUT_MINUTES);
            } catch (Exception ex) {
                log.error("Failed to expire pending payment for order {}", orderId, ex);
            }
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
        couponService.commit(order.getId());

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, previousStatus, OrderStatus.CONFIRMED, null, "VNPay payment succeeded"));
        eventPublisher.publishEvent(new OrderNotificationEvent(NotificationType.ORDER_CONFIRMED, order.getId()));
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
    public PageResponse<OrderResponse> listSellerOrders(User currentUser, Pageable pageable) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());
        // Paginate o cap order (khong phai cap item); moi order chi tra ve item thuoc seller nay
        // (data visibility rule — design doc 0.6). items load theo lo nho @BatchSize tren Order.items.
        return PageResponse.from(
                orderRepository.findAllContainingSellerItems(seller.getId(), pageable)
                        .map(order -> toResponse(order, order.getItems().stream()
                                .filter(item -> item.getSellerId().equals(seller.getId()))
                                .toList())));
    }

    @Override
    @Transactional
    public OrderItemResponse packOrderItem(User currentUser, Long orderItemId) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());

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

        recomputeAggregateStatus(order);

        return toItemResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areSellerItemsPacked(Long orderId, Long sellerId) {
        List<OrderItem> items = orderItemRepository.findAllByOrderIdAndSellerId(orderId, sellerId);
        return !items.isEmpty() && items.stream().allMatch(i -> i.getItemStatus() == OrderItemStatus.PACKED);
    }

    // Thu hang (thap -> cao) dung de tinh aggregate-min orders.status qua cac seller (design doc v2
    // muc 10.4). PENDING_PAYMENT/CANCELLED/COMPLETED... khong nam trong tap nay - xem AGGREGATE_TRACKED_STATUSES.
    private static final Map<OrderStatus, Integer> STATUS_RANK = Map.of(
            OrderStatus.CONFIRMED, 0, OrderStatus.PACKED, 1, OrderStatus.SHIPPED, 2, OrderStatus.DELIVERED, 3);
    private static final Map<Integer, OrderStatus> RANK_STATUS = Map.of(
            0, OrderStatus.CONFIRMED, 1, OrderStatus.PACKED, 2, OrderStatus.SHIPPED, 3, OrderStatus.DELIVERED);
    private static final Set<OrderStatus> AGGREGATE_TRACKED_STATUSES = STATUS_RANK.keySet();

    @Override
    @Transactional
    public void recomputeAggregateStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        recomputeAggregateStatus(order);
    }

    private void recomputeAggregateStatus(Order order) {
        if (!AGGREGATE_TRACKED_STATUSES.contains(order.getStatus())) {
            return; // order da CANCELLED/COMPLETED/dang cho thanh toan... khong con theo doi aggregate nua
        }

        Map<Long, DeliveryStatus> deliveryStatuses = shippingService.getDeliveryStatusesBySeller(order.getId());
        // Seller da bi huy toan bo (item_status = CANCELLED het) la seller "terminal" - loai khoi
        // tap tinh aggregate-min, khong thi se khoa aggregate o hang thap mai mai (design doc muc 10.4/10.5).
        Set<Long> cancelledSellerIds = order.getItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getSellerId))
                .entrySet().stream()
                .filter(e -> e.getValue().stream().allMatch(i -> i.getItemStatus() == OrderItemStatus.CANCELLED))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Set<Long> sellerIds = order.getItems().stream()
                .map(OrderItem::getSellerId)
                .filter(sellerId -> !cancelledSellerIds.contains(sellerId))
                .collect(Collectors.toSet());

        int minRank = sellerIds.stream()
                .mapToInt(sellerId -> rankForSeller(order, sellerId, deliveryStatuses))
                .min()
                .orElse(0);
        OrderStatus newStatus = RANK_STATUS.get(minRank);

        if (newStatus == order.getStatus()) {
            return;
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        orderRepository.save(order);
        // He qua tong hop hanh dong cua nhieu seller/shipper doc lap, khong quy ve 1 actor cu the
        // -> changed_by = null (design doc v2 muc 10.4).
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, previous, newStatus, null, "Aggregate recompute across sellers"));

        if (newStatus == OrderStatus.SHIPPED) {
            eventPublisher.publishEvent(new OrderNotificationEvent(NotificationType.ORDER_SHIPPED, order.getId()));
        } else if (newStatus == OrderStatus.DELIVERED) {
            eventPublisher.publishEvent(new OrderNotificationEvent(NotificationType.ORDER_DELIVERED, order.getId()));
        }
    }

    private int rankForSeller(Order order, Long sellerId, Map<Long, DeliveryStatus> deliveryStatuses) {
        boolean allPacked = order.getItems().stream()
                .filter(item -> item.getSellerId().equals(sellerId))
                .allMatch(item -> item.getItemStatus() == OrderItemStatus.PACKED);
        if (!allPacked) {
            return STATUS_RANK.get(OrderStatus.CONFIRMED);
        }

        DeliveryStatus deliveryStatus = deliveryStatuses.get(sellerId);
        if (deliveryStatus == null) {
            return STATUS_RANK.get(OrderStatus.PACKED);
        }
        // FAILED (con luot retry) van tinh hang SHIPPED — seller do van "dang trong qua trinh giao",
        // chi dang retry (design doc v2 muc 10.4).
        return deliveryStatus == DeliveryStatus.DELIVERED
                ? STATUS_RANK.get(OrderStatus.DELIVERED)
                : STATUS_RANK.get(OrderStatus.SHIPPED);
    }

    private static final int AUTO_COMPLETE_DAYS = 3;

    // TODO (design doc v2 muc 10.5, chua trien khai): het luot retry hien van huy CA order thay vi
    // chi phan cua seller do — interim gap cua giai doan 1 (Shipment-split), se sua khi lam
    // Cancel/Refund per-seller (giai doan 2). Guard `order.getStatus() != SHIPPED` cung chi dung khi
    // seller nay la seller "cham nhat" quyet dinh aggregate; neu seller khac trong cung order dang
    // cham hon (order.status con la CONFIRMED/PACKED) thi guard nay se no-op sai — se sua cung luc.
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
        eventPublisher.publishEvent(new OrderNotificationEvent(NotificationType.ORDER_CANCELLED, order.getId()));

        refundAfterCommit(orderId, "Auto-cancel after 2nd failed delivery attempt");
    }

    // Cung mo hinh voi expirePendingPayments: khong @Transactional, moi order 1 transaction rieng.
    @Override
    public void autoCompleteDeliveredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(AUTO_COMPLETE_DAYS);
        List<Long> orderIds = orderRepository.findIdsByStatusAndUpdatedAtBefore(
                OrderStatus.DELIVERED, cutoff, PageRequest.of(0, MAINTENANCE_BATCH_SIZE));

        for (Long orderId : orderIds) {
            try {
                maintenanceProcessor.completeOne(orderId, AUTO_COMPLETE_DAYS);
            } catch (Exception ex) {
                log.error("Failed to auto-complete delivered order {}", orderId, ex);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Long findEligibleOrderIdForReview(User customer, Long productId) {
        // Khong con suy dien tu order.status IN (DELIVERED, COMPLETED) nhu v1 — voi shipment tach
        // theo seller, mot item du dieu kien review ngay khi seller cua item do da DELIVERED, du cac
        // seller khac trong cung order chua xong (design doc v2 muc 10.6).
        List<OrderItem> candidates = orderItemRepository
                .findAllByProductIdAndOrder_CustomerIdOrderByOrder_CreatedAtDesc(productId, customer.getId());

        for (OrderItem item : candidates) {
            Order order = item.getOrder();
            if (order.getStatus() == OrderStatus.COMPLETED
                    || shippingService.isDeliveredForSeller(order.getId(), item.getSellerId())) {
                return order.getId();
            }
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAllOrders(Pageable pageable) {
        return PageResponse.from(orderRepository.findAll(pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderItemReturnInfo getOrderItemForReturn(Long orderItemId) {
        OrderItem item = orderItemRepository.findById(orderItemId).orElse(null);
        if (item == null) {
            return null;
        }
        Order order = item.getOrder();

        // Doc truc tiep tu delivery cua chinh seller nay (khong con suy dien tu order_status_history
        // cap order — order.status aggregate-min co the con thap hon du seller nay da giao xong, neu
        // seller khac trong cung order dang cham hon; design doc v2 muc 10.6).
        LocalDateTime deliveredAt = shippingService.getDeliveredAtForSeller(order.getId(), item.getSellerId());

        return new OrderItemReturnInfo(
                item.getId(), order.getId(), order.getCustomer().getId(), item.getSellerId(),
                item.getProductId(), item.getProductNameSnapshot(), item.getUnitPriceSnapshot(),
                item.getQuantity(), order.getStatus(), order.getTotalAmount(), order.getDiscountAmount(),
                deliveredAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getCustomerIdByOrderId(Long orderId) {
        return orderRepository.findById(orderId).map(order -> order.getCustomer().getId()).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderItemPayoutInfo> getOrderItemsForPayout(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId).stream()
                .map(item -> new OrderItemPayoutInfo(
                        item.getSellerId(), item.getUnitPriceSnapshot(), item.getQuantity()))
                .toList();
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
                order.getUpdatedAt(),
                order.getCouponCode(),
                order.getDiscountAmount()
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
