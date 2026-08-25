package com.thanhnguyen.ecommercebackend.payment.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentIntentResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentStatusResponse;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{orderId}/intent")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createIntent(
            @AuthenticationPrincipal User currentUser, @PathVariable Long orderId, HttpServletRequest request) {
        PaymentIntentResponse response =
                paymentService.createPaymentIntent(currentUser, orderId, request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatus(
            @AuthenticationPrincipal User currentUser, @PathVariable Long orderId, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(paymentService.getStatus(currentUser, orderId, request.getRemoteAddr())));
    }

    @GetMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> handleIpn(@RequestParam Map<String, String> allParams) {
        return ResponseEntity.ok(paymentService.handleIpn(allParams));
    }
}
