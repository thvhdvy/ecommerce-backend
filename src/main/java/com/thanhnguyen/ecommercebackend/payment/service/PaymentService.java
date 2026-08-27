package com.thanhnguyen.ecommercebackend.payment.service;

import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentDisputeResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentIntentResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.PaymentStatusResponse;
import com.thanhnguyen.ecommercebackend.payment.dto.RefundResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface PaymentService {
    PaymentIntentResponse createPaymentIntent(User currentUser, Long orderId, String clientIp);

    PaymentStatusResponse getStatus(User currentUser, Long orderId, String clientIp);

    Map<String, String> handleIpn(Map<String, String> params);

    /** Trigger refund toàn bộ phần chưa hoàn của payment thuộc order — dùng bởi Order.cancel() và Admin retry. */
    void refund(Long orderId, String reason, String clientIp);

    /**
     * Dùng bởi Return module: hoàn 1 phần tiền cụ thể (đã prorate theo item) cho order, khác
     * refund() ở chỗ không hoàn toàn bộ phần còn lại. Trả về true nếu VNPay xác nhận thành công
     * ngay (đồng bộ, không qua IPN) — false nghĩa là refund đã ghi REFUND_FAILED, cần admin retry
     * qua resolveRefundManually/listFailedRefunds. Xem design doc v2 mục 7.3.
     */
    boolean refundPartial(Long orderId, BigDecimal amount, String reason, String clientIp, Long returnRequestId);

    /** Admin xem danh sách refund đang REFUND_FAILED, cần can thiệp thủ công. */
    PageResponse<RefundResponse> listFailedRefunds(Pageable pageable);

    /** Admin xác nhận đã hoàn tiền cho khách bằng kênh khác (ngoài VNPay), không gọi lại VNPay. */
    void resolveRefundManually(Long refundId, String note);

    /** Admin xem danh sách case IPN bị từ chối do amount không khớp — cần điều tra/đối soát thủ công. */
    PageResponse<PaymentDisputeResponse> listAmountMismatches(Pageable pageable);
}
