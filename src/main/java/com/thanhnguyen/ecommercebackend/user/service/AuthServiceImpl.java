package com.thanhnguyen.ecommercebackend.user.service;

import com.thanhnguyen.ecommercebackend.security.JwtService;
import com.thanhnguyen.ecommercebackend.security.RateLimiter;
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
import com.thanhnguyen.ecommercebackend.user.exception.TooManyAttemptsException;
import com.thanhnguyen.ecommercebackend.user.repository.PasswordResetTokenRepository;
import com.thanhnguyen.ecommercebackend.user.repository.RefreshTokenRepository;
import com.thanhnguyen.ecommercebackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES = 30;
    // Chan brute-force/credential-stuffing tren 1 email cu the — khong khoa account (v1 khong co
    // account-lockout tu dong), chi lam cham lai request toi ve phia attacker.
    private static final int LOGIN_MAX_ATTEMPTS = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int FORGOT_PASSWORD_MAX_ATTEMPTS = 5;
    private static final Duration FORGOT_PASSWORD_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (!rateLimiter.tryAcquire("login:" + request.getEmail(), LOGIN_MAX_ATTEMPTS, LOGIN_WINDOW)) {
            throw new TooManyAttemptsException();
        }

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
        if (!rateLimiter.tryAcquire("forgot-password:" + email, FORGOT_PASSWORD_MAX_ATTEMPTS, FORGOT_PASSWORD_WINDOW)) {
            throw new TooManyAttemptsException();
        }

        userRepository.findByEmail(email).ifPresent(user -> {
            String rawToken = jwtService.generateRawRefreshToken();

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(jwtService.hashToken(rawToken));
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES));
            resetToken.setUsed(false);
            passwordResetTokenRepository.save(resetToken);

            // TODO: integrate real email delivery to send rawToken to the user.
            // Never log the raw token: it is a bearer credential for account takeover.
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
