package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.user.dto.BecomeSellerRequest;
import com.thanhnguyen.ecommercebackend.user.dto.SellerResponse;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.springframework.data.domain.Pageable;

public interface SellerService {
    SellerResponse becomeSeller(User currentUser, BecomeSellerRequest request);

    /**
     * Resolve seller ACTIVE của user — điểm định nghĩa DUY NHẤT cho rule "user phải là seller và
     * seller không bị khóa" (throw NotASellerException/SellerLockedException). Dùng bởi Product/Order
     * module thay vì tự query SellerRepository (giữ module boundary, tránh duplicate rule).
     */
    Seller requireActiveSeller(Long userId);

    /** Admin: xem toàn bộ seller trong hệ thống (paginated). */
    PageResponse<SellerResponse> listAll(Pageable pageable);

    /** Admin: khóa/mở khóa seller — không cascade xóa sản phẩm/order cũ, chỉ chặn hành động mới (tạo/sửa sản phẩm, pack order). */
    SellerResponse setLocked(Long sellerId, boolean locked);
}
