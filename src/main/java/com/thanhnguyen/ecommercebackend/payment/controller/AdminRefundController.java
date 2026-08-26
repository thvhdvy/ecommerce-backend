package com.thanhnguyen.ecommercebackend.payment.controller;

import java.util.Set;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.RefundResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.ResolveRefundRequest;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
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


@RestController
@RequestMapping("/api/admin/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final PaymentService paymentService;

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    @GetMapping("/failed")
    public ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> listFailed(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.listFailedRefunds(PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolve(
            @PathVariable Long id, @Valid @RequestBody ResolveRefundRequest request) {
        paymentService.resolveRefundManually(id, request.getNote());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
