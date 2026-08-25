package com.thanhnguyen.ecommercebackend.user.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.user.dto.LockRequest;
import com.thanhnguyen.ecommercebackend.user.dto.SellerResponse;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sellers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSellerController {

    private final SellerService sellerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SellerResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(sellerService.listAll()));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<SellerResponse>> setLocked(
            @PathVariable Long id, @Valid @RequestBody LockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(sellerService.setLocked(id, request.getLocked())));
    }
}
