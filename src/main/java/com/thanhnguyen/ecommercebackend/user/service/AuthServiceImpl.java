package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.security.JwtService;
import com.thanhnguyen.ecommercebackend.user.dto.LoginRequest;
import com.thanhnguyen.ecommercebackend.user.dto.LoginResponse;
import com.thanhnguyen.ecommercebackend.user.entity.PasswordResetToken;
import com.thanhnguyen.ecommercebackend.user.entity.RefreshToken;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserStatus;
import com.thanhnguyen.ecommercebackend.user.exception.AccountLockedException;
import com.thanhnguyen.ecommercebackend.user.exception.InvalidCredentialsException;
import com.thanhnguyen.ecommercebackend.user.exception.InvalidRefreshTokenException;
import com.thanhnguyen.ecommercebackend.user.exception.InvalidResetTokenException;
import com.thanhnguyen.ecommercebackend.user.repository.PasswordResetTokenRepository;
import com.thanhnguyen.ecommercebackend.user.repository.RefreshTokenRepository;
import com.thanhnguyen.ecommercebackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final long PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException();
        }

        return issueTokens(user);
    }

    @Override
    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshToken existing = findValidRefreshToken(rawRefreshToken);
        User user = existing.getUser();

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException();
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String rawToken = jwtService.generateRawRefreshToken();

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(jwtService.hashToken(rawToken));
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES));
            resetToken.setUsed(false);
            passwordResetTokenRepository.save(resetToken);

            // TODO: integrate real email delivery. For now, log the raw token
            // so the reset flow can be exercised locally/in tests.
            log.info("Password reset token for user {}: {}", user.getEmail(), rawToken);
        });
        // Always return silently regardless of whether the email exists,
        // to avoid leaking which emails are registered (user enumeration).
    }

    @Override
    @Transactional
    public void resetPassword(String rawResetToken, String newPassword) {
        String tokenHash = jwtService.hashToken(rawResetToken);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidResetTokenException::new);

        if (Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidResetTokenException();
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Force logout on all devices after a password reset.
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    private RefreshToken findValidRefreshToken(String rawRefreshToken) {
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (Boolean.TRUE.equals(token.getRevoked()) || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException();
        }

        return token;
    }

    private LoginResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRawRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(jwtService.hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusNanos(jwtService.getRefreshTokenExpirationMs() * 1_000_000));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationMs() / 1000
        );
    }
}
