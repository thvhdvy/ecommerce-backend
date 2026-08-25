package com.thanhnguyen.ecommercebackend.payment.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{id}/refund/retry")
    public ResponseEntity<ApiResponse<Void>> retryRefund(@PathVariable Long id, HttpServletRequest request) {
        paymentService.refund(id, "Admin retry refund", request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
