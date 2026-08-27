package com.thanhnguyen.ecommercebackend.returns.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnResponse;
import com.thanhnguyen.ecommercebackend.returns.service.ReturnService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/seller/returns")
@RequiredArgsConstructor
public class SellerReturnController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    private final ReturnService returnService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> list(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.listSellerReturns(currentUser, PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ReturnResponse>> approve(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.approve(currentUser, id)));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ReturnResponse>> reject(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.reject(currentUser, id)));
    }

    @PatchMapping("/{id}/item-received")
    public ResponseEntity<ApiResponse<ReturnResponse>> markItemReceived(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.markItemReceived(currentUser, id)));
    }
}
