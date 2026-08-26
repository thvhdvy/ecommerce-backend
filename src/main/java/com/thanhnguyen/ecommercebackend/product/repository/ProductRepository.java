package com.thanhnguyen.ecommercebackend.product.repository;

import com.thanhnguyen.ecommercebackend.product.entity.Product;
import com.thanhnguyen.ecommercebackend.product.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

    List<Product> findAllByIdInAndStatus(Collection<Long> ids, ProductStatus status);

    /**
     * Pessimistic write lock — dung khi recalculate rating_avg tu Review module de serialize
     * 2 lan recalculate dong thoi (moi lan la doc AVG roi ghi de, khong co dieu kien WHERE nao
     * bao ve khoi lost-update giua doc va ghi).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForRatingUpdate(@Param("id") Long id);
}
