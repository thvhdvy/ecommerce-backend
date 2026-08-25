package com.thanhnguyen.ecommercebackend.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchCriteria {
    private Long categoryId;
    private Long sellerId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String keyword;
    private BigDecimal minRating;
    private Boolean inStockOnly;
}
