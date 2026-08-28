package com.thanhnguyen.ecommercebackend.product.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.inventory.dto.InventoryResponse;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ImageUploadResponse;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductResponse;
import com.thanhnguyen.ecommercebackend.product.dto.ProductStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductUpdateRequest;
import com.thanhnguyen.ecommercebackend.product.service.ImageUploadService;
import com.thanhnguyen.ecommercebackend.product.service.ProductService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;
    private final ImageUploadService imageUploadService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ProductCreateRequest request) {
        ProductResponse response = productService.create(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.update(currentUser, id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.updateStatus(currentUser, id, request)));
    }

    @PatchMapping("/{id}/inventory")
    public ResponseEntity<ApiResponse<InventoryResponse>> updateInventory(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateInventoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.updateInventory(currentUser, id, request)));
    }

    // Upload doc lap voi 1 product cu the — FE goi endpoint nay truoc de lay url, roi dua url do vao
    // ProductImageRequest luc create/update product (xem product/dto/ProductImageRequest.java).
    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImage(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("file") MultipartFile file) {
        String url = imageUploadService.upload(currentUser, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(new ImageUploadResponse(url)));
    }
}
