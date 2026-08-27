package com.thanhnguyen.ecommercebackend.inventory.service;

import com.thanhnguyen.ecommercebackend.inventory.dto.InventoryResponse;

public interface InventoryService {
    void initializeInventory(Long productId);

    void reserveStock(Long productId, int quantity);

    void releaseStock(Long productId, int quantity);

    void commitReservedStock(Long productId, int quantity);

    /**
     * Dùng bởi Return module khi hàng vật lý quay lại kho sau khi đã bán (ITEM_RECEIVED) — CHỈ cộng
     * thẳng quantityAvailable, không đụng quantityReserved (reserved đã về 0 từ lâu vì order đã
     * CONFIRMED). Khác bản chất với releaseStock() (trả lại chỗ đã giữ nhưng chưa từng giao) — xem
     * design doc v2 mục 7.3, đặt tên hàm khác nhau để tránh nhầm 2 luồng nghiệp vụ khác bản chất.
     */
    void restock(Long productId, int quantity);

    InventoryResponse updateStock(Long productId, int newQuantityAvailable);

    InventoryResponse getByProductId(Long productId);
}
