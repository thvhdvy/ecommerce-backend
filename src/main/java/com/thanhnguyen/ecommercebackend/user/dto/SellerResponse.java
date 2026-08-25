package com.thanhnguyen.ecommercebackend.user.dto;

import com.thanhnguyen.ecommercebackend.user.entity.SellerStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerResponse {
    private Long id;
    private Long userId;
    private String storeName;
    private String storeDescription;
    private SellerStatus status;
    private LocalDateTime createdAt;
}
