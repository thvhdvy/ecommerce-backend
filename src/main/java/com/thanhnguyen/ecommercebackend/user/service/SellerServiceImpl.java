package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.user.dto.BecomeSellerRequest;
import com.thanhnguyen.ecommercebackend.user.dto.SellerResponse;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.SellerStatus;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserRole;
import com.thanhnguyen.ecommercebackend.user.exception.NotEligibleForSellerException;
import com.thanhnguyen.ecommercebackend.user.exception.SellerAlreadyExistsException;
import com.thanhnguyen.ecommercebackend.user.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerServiceImpl implements SellerService {

    private final SellerRepository sellerRepository;

    @Override
    @Transactional
    public SellerResponse becomeSeller(User currentUser, BecomeSellerRequest request) {
        if (currentUser.getRole() != UserRole.CUSTOMER) {
            throw new NotEligibleForSellerException();
        }

        if (sellerRepository.existsByUserId(currentUser.getId())) {
            throw new SellerAlreadyExistsException();
        }

        Seller seller = new Seller();
        seller.setUser(currentUser);
        seller.setStoreName(request.getStoreName());
        seller.setStoreDescription(request.getStoreDescription());
        seller.setStatus(SellerStatus.ACTIVE);

        Seller saved = sellerRepository.save(seller);

        return new SellerResponse(
                saved.getId(),
                currentUser.getId(),
                saved.getStoreName(),
                saved.getStoreDescription(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }
}
