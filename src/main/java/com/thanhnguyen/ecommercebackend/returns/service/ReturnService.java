package com.thanhnguyen.ecommercebackend.returns.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnCreateRequest;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.springframework.data.domain.Pageable;

public interface ReturnService {
    /** Customer tạo yêu cầu return cho 1 order_item — validate eligibility, prorate discount, giữ chỗ (design doc v2 mục 7.3). */
    ReturnResponse create(User currentUser, ReturnCreateRequest request);

    PageResponse<ReturnResponse> listMyReturns(User currentUser, Pageable pageable);

    /** Customer tự hủy — chỉ khi còn REQUESTED. */
    ReturnResponse cancel(User currentUser, Long returnId);

    /** Seller xem return request cho order_item thuộc mình. */
    PageResponse<ReturnResponse> listSellerReturns(User currentUser, Pageable pageable);

    ReturnResponse approve(User currentUser, Long returnId);

    ReturnResponse reject(User currentUser, Long returnId);

    /** Seller xác nhận đã nhận lại hàng — restock + trigger refund thật (network call, ngoài transaction). */
    ReturnResponse markItemReceived(User currentUser, Long returnId);

    /** Admin: toàn hệ thống. */
    PageResponse<ReturnResponse> listAllReturns(Pageable pageable);

    /** Admin can thiệp khi seller không xử lý — hiệu lực như approve() nhưng không check ownership. */
    ReturnResponse forceApprove(Long returnId);

    /** Admin: thử refund lại cho return đang REFUND_FAILED. */
    ReturnResponse retryRefund(Long returnId);

    /** Scheduled job: APPROVED quá RETURN_SHIP_BACK_DAYS mà chưa ITEM_RECEIVED → tự động EXPIRED. */
    void autoExpireApprovedReturns();
}
