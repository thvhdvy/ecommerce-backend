package com.thanhnguyen.ecommercebackend.product.specification;

import com.thanhnguyen.ecommercebackend.product.dto.ProductSearchCriteria;
import com.thanhnguyen.ecommercebackend.product.entity.Product;
import com.thanhnguyen.ecommercebackend.product.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> fromCriteria(ProductSearchCriteria criteria) {
        // Specification.and(null) nem IllegalArgumentException (khong null-safe) — nen loc null truoc khi ghep.
        return Arrays.<Specification<Product>>asList(
                        hasStatus(ProductStatus.ACTIVE),
                        hasCategory(criteria.getCategoryId()),
                        hasSeller(criteria.getSellerId()),
                        minPrice(criteria.getMinPrice()),
                        maxPrice(criteria.getMaxPrice()),
                        nameContains(criteria.getKeyword()),
                        minRating(criteria.getMinRating()),
                        inStockOnly(criteria.getInStockOnly()))
                .stream()
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElseThrow();
    }

    private static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Product> hasCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    private static Specification<Product> hasSeller(Long sellerId) {
        if (sellerId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("seller").get("id"), sellerId);
    }

    private static Specification<Product> minPrice(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    private static Specification<Product> maxPrice(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    private static Specification<Product> nameContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    private static Specification<Product> minRating(BigDecimal minRating) {
        if (minRating == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("ratingAvg"), minRating);
    }

    private static Specification<Product> inStockOnly(Boolean inStockOnly) {
        if (inStockOnly == null || !inStockOnly) {
            return null;
        }
        return (root, query, cb) -> cb.isTrue(root.get("inStock"));
    }
}
