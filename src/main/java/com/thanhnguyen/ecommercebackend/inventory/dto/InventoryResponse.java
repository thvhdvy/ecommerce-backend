package com.thanhnguyen.ecommercebackend.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private Long productId;
    private Integer quantityAvailable;
    private Integer quantityReserved;
}
