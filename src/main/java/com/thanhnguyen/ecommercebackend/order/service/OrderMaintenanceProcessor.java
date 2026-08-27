package com.thanhnguyen.ecommercebackend.order.service;

import com.thanhnguyen.ecommercebackend.coupon.service.CouponService;
import com.thanhnguyen.ecommercebackend.inventory.service.InventoryService;
import com.thanhnguyen.ecommercebackend.order.entity.Order;
import com.thanhnguyen.ecommercebackend.order.entity.OrderItem;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatusHistory;
import com.thanhnguyen.ecommercebackend.order.repository.OrderRepository;
import com.thanhnguyen.ecommercebackend.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xu ly TUNG order trong 1 transaction rieng cho cac scheduled job (payment timeout,
 * auto-complete). Tach ra component rieng vi 2 ly do:
 * - REQUIRES_NEW per order: 1 order loi (vd du lieu inventory lech) chi rollback rieng order do,
 *   khong keo ca batch rollback theo — truoc day 1 order "doc" se chan worker vinh vien vi moi
 *   lan chay lai gap dung order do dau tien va rollback het.
 * - Goi qua bean rieng de @Transactional di qua proxy (self-invocation trong OrderServiceImpl
 *   khong mo transaction moi — cung ly do voi PaymentResultApplier).
 * Moi method tu doc lai order va re-check status trong transaction cua minh — an toan neu status
 * da doi giua luc quet id va luc xu ly (vd user vua thanh toan xong).
 */
@Component
@RequiredArgsConstructor
class OrderMaintenanceProcessor {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void expireOne(Long orderId, int timeoutMinutes) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return; // da thanh toan/huy trong luc cho xu ly — bo qua
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseStock(item.getProductId(), item.getQuantity());
        }
        couponService.release(order.getId());

        order.setStatus(OrderStatus.PAYMENT_EXPIRED);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_EXPIRED, null,
                "Payment timeout after " + timeoutMinutes + " minutes"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void completeOne(Long orderId, int autoCompleteDays) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.DELIVERED) {
            return;
        }

        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, OrderStatus.DELIVERED, OrderStatus.COMPLETED, null,
                "Auto-completed after " + autoCompleteDays + " days"));
    }
}
