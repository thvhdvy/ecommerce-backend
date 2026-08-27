package com.thanhnguyen.ecommercebackend.coupon.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponCreateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponRedemptionResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponUpdateRequest;
import com.thanhnguyen.ecommercebackend.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "code", "endsAt");

    private final CouponService couponService;

    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> create(@Valid @RequestBody CouponCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(couponService.create(request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CouponUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(couponService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CouponResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody CouponStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(couponService.updateStatus(id, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                couponService.list(PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }

    @GetMapping("/{id}/redemptions")
    public ResponseEntity<ApiResponse<PageResponse<CouponRedemptionResponse>>> listRedemptions(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                couponService.listRedemptions(id, PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }
}
