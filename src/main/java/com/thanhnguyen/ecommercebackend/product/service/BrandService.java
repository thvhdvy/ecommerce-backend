package com.thanhnguyen.ecommercebackend.product.service;

import com.thanhnguyen.ecommercebackend.product.dto.BrandRequest;
import com.thanhnguyen.ecommercebackend.product.dto.BrandResponse;

public interface BrandService {
    BrandResponse create(BrandRequest request);

    BrandResponse update(Long id, BrandRequest request);
}
