package com.thanhnguyen.ecommercebackend.order.controller;

import com.thanhnguyen.ecommercebackend.common.PageRequests;
import org.springframework.data.domain.Pageable;

import java.util.Set;

/**
 * Sort whitelist dung chung cho 3 endpoint list order (OrderController, AdminOrderController,
 * SellerOrderController) — cap size + validate delegate ve common PageRequests.
 */
final class OrderPageables {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalAmount");

    private OrderPageables() {
    }

    static Pageable capped(Pageable pageable) {
        return PageRequests.capped(pageable, SORTABLE_FIELDS);
    }
}
