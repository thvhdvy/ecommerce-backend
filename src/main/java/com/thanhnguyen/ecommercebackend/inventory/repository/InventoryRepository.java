package com.thanhnguyen.ecommercebackend.inventory.repository;

import com.thanhnguyen.ecommercebackend.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(Long productId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantityAvailable = i.quantityAvailable - :qty, "
            + "i.quantityReserved = i.quantityReserved + :qty, i.version = i.version + 1, "
            + "i.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE i.productId = :productId AND i.quantityAvailable >= :qty")
    int reserveStock(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantityAvailable = i.quantityAvailable + :qty, "
            + "i.quantityReserved = i.quantityReserved - :qty, i.version = i.version + 1, "
            + "i.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE i.productId = :productId AND i.quantityReserved >= :qty")
    int releaseStock(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.quantityReserved = i.quantityReserved - :qty, i.version = i.version + 1, "
            + "i.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE i.productId = :productId AND i.quantityReserved >= :qty")
    int commitReservedStock(@Param("productId") Long productId, @Param("qty") int qty);
}
