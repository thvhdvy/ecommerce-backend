package com.thanhnguyen.ecommercebackend.payout.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.LedgerEntryResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.PayoutResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.SellerBalanceResponse;
import com.thanhnguyen.ecommercebackend.payout.service.PayoutService;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
public class SellerPayoutController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    private final PayoutService payoutService;
    private final SellerService sellerService;

    @GetMapping("/api/seller/ledger")
    public ResponseEntity<ApiResponse<PageResponse<LedgerEntryResponse>>> ledger(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(
                payoutService.listLedger(seller.getId(), PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }

    @GetMapping("/api/seller/balance")
    public ResponseEntity<ApiResponse<SellerBalanceResponse>> balance(@AuthenticationPrincipal User currentUser) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(payoutService.getBalance(seller.getId())));
    }

    @GetMapping("/api/seller/payouts")
    public ResponseEntity<ApiResponse<PageResponse<PayoutResponse>>> payouts(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(
                payoutService.listPayoutsForSeller(seller.getId(), PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }
}
