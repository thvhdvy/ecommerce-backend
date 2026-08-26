package com.thanhnguyen.ecommercebackend.review.controller;

import java.util.Set;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
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


@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "rating");

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> listByProduct(
            @AuthenticationPrincipal User currentUser, @PathVariable Long productId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.listByProduct(productId, currentUserId, PageRequests.capped(pageable, SORTABLE_FIELDS))));
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
