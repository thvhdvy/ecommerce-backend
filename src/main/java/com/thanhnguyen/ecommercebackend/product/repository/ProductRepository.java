package com.thanhnguyen.ecommercebackend.product.repository;

import com.thanhnguyen.ecommercebackend.product.entity.Product;
import com.thanhnguyen.ecommercebackend.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);
}
