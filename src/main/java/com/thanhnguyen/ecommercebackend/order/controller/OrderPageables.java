package com.thanhnguyen.ecommercebackend.order.controller;

import com.thanhnguyen.ecommercebackend.product.exception.InvalidSortFieldException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Cap page size + whitelist sort field cho cac endpoint list order (cung pattern voi
 * ProductController.cappedPageable/validateSort) — dung chung cho OrderController,
 * AdminOrderController, SellerOrderController de khong duplicate 3 lan.
 */
final class OrderPageables {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalAmount");

    private OrderPageables() {
    }

    static Pageable capped(Pageable pageable) {
        validateSort(pageable.getSort());
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidSortFieldException(order.getProperty());
            }
        }
    }
}
