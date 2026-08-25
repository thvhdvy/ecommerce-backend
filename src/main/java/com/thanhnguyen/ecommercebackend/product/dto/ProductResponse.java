package com.thanhnguyen.ecommercebackend.product.dto;

import com.thanhnguyen.ecommercebackend.product.entity.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private Long sellerId;
    private Long categoryId;
    private Long brandId;
    private String name;
    private String description;
    private BigDecimal price;
    private ProductStatus status;
    private BigDecimal ratingAvg;
    private List<ProductImageResponse> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
