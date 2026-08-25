package com.thanhnguyen.ecommercebackend.inventory.service;

import com.thanhnguyen.ecommercebackend.inventory.dto.InventoryResponse;

public interface InventoryService {
    void initializeInventory(Long productId);

    void reserveStock(Long productId, int quantity);

    void releaseStock(Long productId, int quantity);

    void commitReservedStock(Long productId, int quantity);

    InventoryResponse updateStock(Long productId, int newQuantityAvailable);

    InventoryResponse getByProductId(Long productId);
}
