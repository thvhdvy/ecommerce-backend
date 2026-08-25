package com.thanhnguyen.ecommercebackend.user.repository;

import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
