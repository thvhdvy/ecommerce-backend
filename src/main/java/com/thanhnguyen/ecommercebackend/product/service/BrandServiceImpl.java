package com.thanhnguyen.ecommercebackend.product.service;

import com.thanhnguyen.ecommercebackend.product.dto.BrandRequest;
import com.thanhnguyen.ecommercebackend.product.dto.BrandResponse;
import com.thanhnguyen.ecommercebackend.product.entity.Brand;
import com.thanhnguyen.ecommercebackend.product.exception.BrandNotFoundException;
import com.thanhnguyen.ecommercebackend.product.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;

    @Override
    @Transactional
    public BrandResponse create(BrandRequest request) {
        Brand brand = new Brand();
        brand.setName(request.getName());

        Brand saved = brandRepository.save(brand);
        return new BrandResponse(saved.getId(), saved.getName());
    }

    @Override
    @Transactional
    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new BrandNotFoundException(id));

        brand.setName(request.getName());

        Brand saved = brandRepository.save(brand);
        return new BrandResponse(saved.getId(), saved.getName());
    }
}
