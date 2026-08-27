package com.thanhnguyen.ecommercebackend.order.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.order.dto.OrderResponse;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Namespace /api/admin/orders/** duoc chia se co chu dich giua 3 controller theo module ownership
// (order/payment/shipping — xem design doc muc 0.7): moi controller so huu dung cac sub-path thuoc
// nghiep vu module do (order: list/cancel; payment: AdminPaymentController — refund/retry;
// shipping: AdminShippingController — assign-shipper/confirm-delivery). Day la REST sub-resource
// hop le (action tren order do module khac xu ly), khong phai loi to chuc — nhung can luu y khi
// tim "endpoint nao xu ly gi tren order" phai xem ca 3 file.
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> listAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.listAllOrders(OrderPageables.capped(pageable))));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> forceCancel(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.forceCancel(currentUser, id)));
    }

    @PostMapping("/{id}/sellers/{sellerId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> forceCancelSellerItems(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id, @PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.forceCancelSellerItems(currentUser, id, sellerId)));
    }
}
