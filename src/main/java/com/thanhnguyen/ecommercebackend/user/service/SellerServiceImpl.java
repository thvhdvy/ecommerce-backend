package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.user.dto.BecomeSellerRequest;
import com.thanhnguyen.ecommercebackend.user.dto.SellerResponse;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.SellerStatus;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserRole;
import com.thanhnguyen.ecommercebackend.user.exception.NotEligibleForSellerException;
import com.thanhnguyen.ecommercebackend.user.exception.SellerAlreadyExistsException;
import com.thanhnguyen.ecommercebackend.user.exception.SellerNotFoundException;
import com.thanhnguyen.ecommercebackend.user.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerResponse> listAll() {
        return sellerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SellerResponse setLocked(Long sellerId, boolean locked) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));

        seller.setStatus(locked ? SellerStatus.LOCKED : SellerStatus.ACTIVE);
        Seller saved = sellerRepository.save(seller);

        return toResponse(saved);
    }

    private SellerResponse toResponse(Seller seller) {
        return new SellerResponse(
                seller.getId(),
                seller.getUser().getId(),
                seller.getStoreName(),
                seller.getStoreDescription(),
                seller.getStatus(),
                seller.getCreatedAt()
        );
    }
}
