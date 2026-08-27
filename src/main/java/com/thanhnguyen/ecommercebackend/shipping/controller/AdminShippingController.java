package com.thanhnguyen.ecommercebackend.shipping.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.AssignShipperRequest;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryResponse;
import com.thanhnguyen.ecommercebackend.shipping.service.ShippingService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// So huu sub-path /assign-shipper, /confirm-delivery cua namespace /api/admin/orders/** — xem
// AdminOrderController cho ly do namespace nay duoc chia se giua order/payment/shipping.
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShippingController {

    private final ShippingService shippingService;

    @PatchMapping("/{id}/assign-shipper")
    public ResponseEntity<ApiResponse<DeliveryResponse>> assignShipper(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id, @Valid @RequestBody AssignShipperRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingService.assignShipper(id, request.getSellerId(), request.getShipperId(), currentUser)));
    }

    @PatchMapping("/{id}/confirm-delivery")
    public ResponseEntity<ApiResponse<DeliveryResponse>> confirmDelivery(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id,
            @RequestParam Long sellerId) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingService.confirmDeliveredManually(currentUser, id, sellerId)));
    }
}
