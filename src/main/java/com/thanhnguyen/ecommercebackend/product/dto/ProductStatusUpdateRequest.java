package com.thanhnguyen.ecommercebackend.product.dto;

import com.thanhnguyen.ecommercebackend.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatusUpdateRequest {
    @NotNull
    private ProductStatus status;
}
