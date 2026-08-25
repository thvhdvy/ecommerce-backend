package com.thanhnguyen.ecommercebackend.review.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.review.dto.CreateReviewRequest;
import com.thanhnguyen.ecommercebackend.review.dto.ReviewResponse;
import com.thanhnguyen.ecommercebackend.review.service.ReviewService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> listByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.listVisibleByProduct(productId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long productId,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.create(currentUser, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
