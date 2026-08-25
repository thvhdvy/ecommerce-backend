package com.thanhnguyen.ecommercebackend.shipping.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.shipping.service.ShippingService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shipper/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SHIPPER')")
public class ShipperDeliveryController {

    private final ShippingService shippingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeliveryResponse>>> listMyDeliveries(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.listMyDeliveries(currentUser)));
    }

    @PatchMapping("/{deliveryId}/status")
    public ResponseEntity<ApiResponse<DeliveryResponse>> updateStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long deliveryId,
            @Valid @RequestBody DeliveryStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingService.updateDeliveryStatus(currentUser, deliveryId, request)));
    }
}
