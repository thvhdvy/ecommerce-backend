package com.thanhnguyen.ecommercebackend.coupon.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponValidateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponValidationResponse;
import com.thanhnguyen.ecommercebackend.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validate(
            @Valid @RequestBody CouponValidateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                couponService.validate(request.getCode(), request.getCartTotal())));
    }
}
