package com.thanhnguyen.ecommercebackend.review.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.review.dto.ReviewResponse;
import com.thanhnguyen.ecommercebackend.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;

    @PatchMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<ReviewResponse>> hide(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.hide(id)));
    }
}
