package com.thanhnguyen.ecommercebackend.order.dto;

import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Du lieu chi-doc ve 1 order_item can cho module Return (ownership, eligibility, tinh refund) —
 * tra ve qua OrderService.getOrderItemForReturn() thay vi Return module tu query thang
 * OrderRepository/OrderItemRepository (giu module boundary, design doc muc 0.8).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemReturnInfo {
    private Long orderItemId;
    private Long orderId;
    private Long customerId;
    private Long sellerId;
    private Long productId;
    private String productNameSnapshot;
    private BigDecimal unitPriceSnapshot;
    private Integer quantity;
    private OrderStatus orderStatus;
    private BigDecimal orderTotalAmount;
    private BigDecimal orderDiscountAmount;
    // Null neu order chua tung DELIVERED (VD van con SHIPPED) — dung tinh RETURN_WINDOW_DAYS.
    private LocalDateTime deliveredAt;
}
