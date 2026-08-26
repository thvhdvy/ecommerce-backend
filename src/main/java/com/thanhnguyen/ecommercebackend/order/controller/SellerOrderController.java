package com.thanhnguyen.ecommercebackend.order.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemResponse;
import com.thanhnguyen.ecommercebackend.order.dto.OrderResponse;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderService orderService;

    @GetMapping("/api/seller/orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> listSellerOrders(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.listSellerOrders(currentUser, OrderPageables.capped(pageable))));
    }

    @PatchMapping("/api/seller/order-items/{orderItemId}/status")
    public ResponseEntity<ApiResponse<OrderItemResponse>> packOrderItem(
            @AuthenticationPrincipal User currentUser, @PathVariable Long orderItemId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.packOrderItem(currentUser, orderItemId)));
    }
}
