package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.user.dto.RegisterRequest;
import com.thanhnguyen.ecommercebackend.user.dto.UpdateProfileRequest;
import com.thanhnguyen.ecommercebackend.user.dto.UserResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse register(RegisterRequest request);

    /**
     * Lookup User entity theo id cho module khác cần giữ FK thật tới User (VD: Shipping gán
     * Delivery.shipper) — throw UserNotFoundException nếu không tồn tại. Rule nghiệp vụ trên user
     * tìm được (VD: phải có role SHIPPER) thuộc về module gọi, không check ở đây.
     */
    User getEntityById(Long userId);

    UserResponse getProfile(Long userId);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    /** Admin: xem toàn bộ user trong hệ thống (paginated). */
    PageResponse<UserResponse> listAll(Pageable pageable);

    /**
     * Admin: khóa/mở khóa tài khoản. Khóa thì revoke luôn toàn bộ refresh_tokens hiện có
     * (JWT access token cũ vẫn bị chặn ở request kế tiếp qua JwtAuthenticationFilter re-check status).
     * Không cho phép admin tự khóa chính mình (actorUserId == userId) — vì filter re-check status
     * mỗi request, tự khóa sẽ tự chặn luôn request unlock kế tiếp, không có đường phục hồi qua API.
     */
    UserResponse setLocked(Long actorUserId, Long userId, boolean locked);
}
