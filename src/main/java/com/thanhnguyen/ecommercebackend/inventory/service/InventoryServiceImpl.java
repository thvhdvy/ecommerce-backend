package com.thanhnguyen.ecommercebackend.inventory.service;

import com.thanhnguyen.ecommercebackend.inventory.dto.InventoryResponse;
import com.thanhnguyen.ecommercebackend.inventory.entity.Inventory;
import com.thanhnguyen.ecommercebackend.inventory.exception.InsufficientStockException;
import com.thanhnguyen.ecommercebackend.inventory.exception.InventoryNotFoundException;
import com.thanhnguyen.ecommercebackend.inventory.repository.InventoryRepository;
import com.thanhnguyen.ecommercebackend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductService productService;

    // ProductServiceImpl phu thuoc InventoryService (initializeInventory/updateStock) —
    // @Lazy o chieu Inventory->Product (chi dung de dong bo co inStock) de pha vong lap khoi tao bean.
    public InventoryServiceImpl(InventoryRepository inventoryRepository, @Lazy ProductService productService) {
        this.inventoryRepository = inventoryRepository;
        this.productService = productService;
    }

    @Override
    @Transactional
    public void initializeInventory(Long productId) {
        inventoryRepository.save(new Inventory(productId));
    }

    @Override
    @Transactional
    public void reserveStock(Long productId, int quantity) {
        int updated = inventoryRepository.reserveStock(productId, quantity);
        if (updated == 0) {
            throw new InsufficientStockException(productId);
        }
        syncStockFlag(productId);
    }

    @Override
    @Transactional
    public void releaseStock(Long productId, int quantity) {
        int updated = inventoryRepository.releaseStock(productId, quantity);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Failed to release reserved stock for product " + productId + " — data inconsistency");
        }
        syncStockFlag(productId);
    }

    @Override
    @Transactional
    public void commitReservedStock(Long productId, int quantity) {
        int updated = inventoryRepository.commitReservedStock(productId, quantity);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Failed to commit reserved stock for product " + productId + " — data inconsistency");
        }
    }

    @Override
    @Transactional
    public InventoryResponse updateStock(Long productId, int newQuantityAvailable) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        inventory.setQuantityAvailable(newQuantityAvailable);
        Inventory saved = inventoryRepository.save(inventory);
        productService.updateStockFlag(productId, newQuantityAvailable > 0);

        return toResponse(saved);
    }

    @Override
    public InventoryResponse getByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        return toResponse(inventory);
    }

    private void syncStockFlag(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
        productService.updateStockFlag(productId, inventory.getQuantityAvailable() > 0);
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProductId(),
                inventory.getQuantityAvailable(),
                inventory.getQuantityReserved()
        );
    }
}
