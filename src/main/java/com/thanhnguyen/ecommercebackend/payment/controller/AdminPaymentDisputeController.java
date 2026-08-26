package com.thanhnguyen.ecommercebackend.payment.controller;

import java.util.Set;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentDisputeResponse;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentDisputeController {

    private final PaymentService paymentService;

    private static final Set<String> SORTABLE_FIELDS = Set.of("processedAt");

    @GetMapping("/amount-mismatches")
    public ResponseEntity<ApiResponse<PageResponse<PaymentDisputeResponse>>> listAmountMismatches(
            @PageableDefault(size = 20, sort = "processedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.listAmountMismatches(PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }
}
