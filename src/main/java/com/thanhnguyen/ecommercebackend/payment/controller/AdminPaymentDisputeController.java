package com.thanhnguyen.ecommercebackend.payment.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentDisputeResponse;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentDisputeController {

    private final PaymentService paymentService;

    @GetMapping("/amount-mismatches")
    public ResponseEntity<ApiResponse<List<PaymentDisputeResponse>>> listAmountMismatches() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.listAmountMismatches()));
    }
}
