package com.thanhnguyen.ecommercebackend.returns.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnResponse;
import com.thanhnguyen.ecommercebackend.returns.service.ReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/returns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReturnController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    private final ReturnService returnService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.listAllReturns(PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }

    @PostMapping("/{id}/force-approve")
    public ResponseEntity<ApiResponse<ReturnResponse>> forceApprove(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.forceApprove(id)));
    }

    @PostMapping("/{id}/refund/retry")
    public ResponseEntity<ApiResponse<ReturnResponse>> retryRefund(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.retryRefund(id)));
    }
}
