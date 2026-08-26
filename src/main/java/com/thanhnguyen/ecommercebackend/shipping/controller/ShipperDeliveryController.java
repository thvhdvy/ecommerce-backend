package com.thanhnguyen.ecommercebackend.shipping.controller;

import java.util.Set;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
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


@RestController
@RequestMapping("/api/shipper/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SHIPPER')")
public class ShipperDeliveryController {

    private final ShippingService shippingService;

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DeliveryResponse>>> listMyDeliveries(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                shippingService.listMyDeliveries(currentUser, PageRequests.capped(pageable, SORTABLE_FIELDS))));
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
