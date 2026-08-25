package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.user.dto.LoginRequest;
import com.thanhnguyen.ecommercebackend.user.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);

    LoginResponse refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    void forgotPassword(String email);

    void resetPassword(String rawResetToken, String newPassword);
}
