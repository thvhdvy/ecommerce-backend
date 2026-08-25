package com.thanhnguyen.ecommercebackend.payment.service;

import com.thanhnguyen.ecommercebackend.payment.dto.PaymentIntentResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentStatusResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.util.Map;

public interface PaymentService {
    PaymentIntentResponse createPaymentIntent(User currentUser, Long orderId, String clientIp);

    PaymentStatusResponse getStatus(User currentUser, Long orderId, String clientIp);

    Map<String, String> handleIpn(Map<String, String> params);

    /** Trigger refund toàn bộ phần chưa hoàn của payment thuộc order — dùng bởi Order.cancel() và Admin retry. */
    void refund(Long orderId, String reason, String clientIp);
}
