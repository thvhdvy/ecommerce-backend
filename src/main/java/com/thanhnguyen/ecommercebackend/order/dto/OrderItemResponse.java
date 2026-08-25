package com.thanhnguyen.ecommercebackend.order.dto;

import com.thanhnguyen.ecommercebackend.order.entity.OrderItemStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private Long sellerId;
    private String productName;
    private BigDecimal unitPrice;
    private int quantity;
    private OrderItemStatus itemStatus;
}
