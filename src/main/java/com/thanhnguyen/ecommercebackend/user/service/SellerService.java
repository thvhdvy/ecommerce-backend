package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.user.dto.BecomeSellerRequest;
import com.thanhnguyen.ecommercebackend.user.dto.SellerResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;

import java.util.List;

public interface SellerService {
    SellerResponse becomeSeller(User currentUser, BecomeSellerRequest request);

    /** Admin: xem toàn bộ seller trong hệ thống. */
    List<SellerResponse> listAll();

    /** Admin: khóa/mở khóa seller — không cascade xóa sản phẩm/order cũ, chỉ chặn hành động mới (tạo/sửa sản phẩm, pack order). */
    SellerResponse setLocked(Long sellerId, boolean locked);
}
