package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.user.dto.RegisterRequest;
import com.thanhnguyen.ecommercebackend.user.dto.UpdateProfileRequest;
import com.thanhnguyen.ecommercebackend.user.dto.UserResponse;

public interface UserService {
    UserResponse register(RegisterRequest request);

    UserResponse getProfile(Long userId);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);
}
