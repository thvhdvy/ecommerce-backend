package com.thanhnguyen.ecommercebackend.product.repository;

import com.thanhnguyen.ecommercebackend.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
