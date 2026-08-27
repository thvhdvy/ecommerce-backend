package com.thanhnguyen.ecommercebackend.coupon.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponCreateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponRedemptionResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponUpdateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponValidationResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface CouponService {
    CouponResponse create(CouponCreateRequest request);

    CouponResponse update(Long id, CouponUpdateRequest request);

    CouponResponse updateStatus(Long id, CouponStatusUpdateRequest request);

    PageResponse<CouponResponse> list(Pageable pageable);

    PageResponse<CouponRedemptionResponse> listRedemptions(Long couponId, Pageable pageable);

    /** Preview discount — CHỈ kiểm tra điều kiện + tính số tiền giảm, KHÔNG giữ chỗ lượt dùng. */
    CouponValidationResponse validate(String code, BigDecimal cartTotal);

    /**
     * Giữ chỗ 1 lượt dùng coupon cho order — gọi bên trong transaction checkout (OrderService),
     * throw nếu coupon không hợp lệ/hết lượt/user đã dùng. Không có compensation logic thủ công:
     * nếu bước sau (VD unique constraint per-user) thất bại, exception lan ra ngoài làm rollback
     * toàn bộ transaction checkout, tự động hoàn tác luôn phần usage_reserved vừa cộng ở đây.
     */
    BigDecimal reserve(User currentUser, String code, Long orderId, BigDecimal orderSubtotal);

    /** Payment thành công → chuyển RESERVED sang COMMITTED (lượt dùng coi như đã tiêu thật). No-op nếu order không dùng coupon. */
    void commit(Long orderId);

    /** Cancel/expire TRƯỚC khi thanh toán → trả lại lượt dùng. No-op nếu order không dùng coupon hoặc đã COMMITTED/RELEASED. */
    void release(Long orderId);
}
