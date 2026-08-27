package com.thanhnguyen.ecommercebackend.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Du lieu chi-doc ve 1 order_item can cho module Payout de tinh EARNED ledger entry theo seller
 * (design doc v2 muc 9.3) — tra ve qua OrderService.getOrderItemsForPayout() thay vi Payout module
 * tu query thang OrderItemRepository (giu module boundary, design doc muc 0.8).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemPayoutInfo {
    private Long sellerId;
    private BigDecimal unitPriceSnapshot;
    private Integer quantity;
}
