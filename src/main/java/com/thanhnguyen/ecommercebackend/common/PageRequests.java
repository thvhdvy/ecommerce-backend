package com.thanhnguyen.ecommercebackend.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Cap page size + whitelist sort field cho cac endpoint list — dung chung toan bo controller
 * (Product, Order, Review, cac admin listing...) de khong duplicate logic nay moi noi mot ban.
 * Sort field ngoai whitelist -> InvalidSortFieldException (400), size vuot tran -> cat ve MAX.
 */
public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable capped(Pageable pageable, Set<String> sortableFields) {
        validateSort(pageable.getSort(), sortableFields);
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    private static void validateSort(Sort sort, Set<String> sortableFields) {
        for (Sort.Order order : sort) {
            if (!sortableFields.contains(order.getProperty())) {
                throw new InvalidSortFieldException(order.getProperty());
            }
        }
    }
}
