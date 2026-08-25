package com.thanhnguyen.ecommercebackend.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BecomeSellerRequest {
    @NotBlank
    private String storeName;

    private String storeDescription;
}
