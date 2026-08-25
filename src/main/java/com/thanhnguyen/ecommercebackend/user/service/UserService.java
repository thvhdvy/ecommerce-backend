package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.user.dto.RegisterRequest;
import com.thanhnguyen.ecommercebackend.user.dto.UpdateProfileRequest;
import com.thanhnguyen.ecommercebackend.user.dto.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse register(RegisterRequest request);

    UserResponse getProfile(Long userId);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    /** Admin: xem toàn bộ user trong hệ thống. */
    List<UserResponse> listAll();

    /**
     * Admin: khóa/mở khóa tài khoản. Khóa thì revoke luôn toàn bộ refresh_tokens hiện có
     * (JWT access token cũ vẫn bị chặn ở request kế tiếp qua JwtAuthenticationFilter re-check status).
     * Không cho phép admin tự khóa chính mình (actorUserId == userId) — vì filter re-check status
     * mỗi request, tự khóa sẽ tự chặn luôn request unlock kế tiếp, không có đường phục hồi qua API.
     */
    UserResponse setLocked(Long actorUserId, Long userId, boolean locked);
}
