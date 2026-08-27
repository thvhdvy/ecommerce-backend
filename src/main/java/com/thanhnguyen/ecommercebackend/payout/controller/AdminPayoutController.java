package com.thanhnguyen.ecommercebackend.payout.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.PayoutResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.SellerBalanceResponse;
import com.thanhnguyen.ecommercebackend.payout.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPayoutController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    private final PayoutService payoutService;

    @GetMapping("/api/admin/sellers/{id}/balance")
    public ResponseEntity<ApiResponse<SellerBalanceResponse>> getBalance(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.getBalance(id)));
    }

    @PostMapping("/api/admin/sellers/{id}/payouts")
    public ResponseEntity<ApiResponse<PayoutResponse>> payOut(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.payOut(id)));
    }

    @GetMapping("/api/admin/payouts")
    public ResponseEntity<ApiResponse<PageResponse<PayoutResponse>>> listAll(
            @RequestParam(required = false) Long sellerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                payoutService.listAllPayouts(sellerId, PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }
}
