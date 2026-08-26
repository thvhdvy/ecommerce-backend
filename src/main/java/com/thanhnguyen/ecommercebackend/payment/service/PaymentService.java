package com.thanhnguyen.ecommercebackend.payment.service;

import com.thanhnguyen.ecommercebackend.payment.dto.PaymentDisputeResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentIntentResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentStatusResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.RefundResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.util.List;
import java.util.Map;

public interface PaymentService {
    PaymentIntentResponse createPaymentIntent(User currentUser, Long orderId, String clientIp);

    PaymentStatusResponse getStatus(User currentUser, Long orderId, String clientIp);

    Map<String, String> handleIpn(Map<String, String> params);

    /** Trigger refund toàn bộ phần chưa hoàn của payment thuộc order — dùng bởi Order.cancel() và Admin retry. */
    void refund(Long orderId, String reason, String clientIp);

    /** Admin xem danh sách refund đang REFUND_FAILED, cần can thiệp thủ công. */
    List<RefundResponse> listFailedRefunds();

    /** Admin xác nhận đã hoàn tiền cho khách bằng kênh khác (ngoài VNPay), không gọi lại VNPay. */
    void resolveRefundManually(Long refundId, String note);

    /** Admin xem danh sách case IPN bị từ chối do amount không khớp — cần điều tra/đối soát thủ công. */
    List<PaymentDisputeResponse> listAmountMismatches();
}
