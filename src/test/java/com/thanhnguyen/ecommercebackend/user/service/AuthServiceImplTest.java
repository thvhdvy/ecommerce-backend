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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hashedPassword");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void login_shouldReturnTokens_whenCredentialsValid() {
        User user = activeUser();
        LoginRequest request = new LoginRequest("user@example.com", "plainPassword");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plainPassword", "hashedPassword")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRawRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        LoginResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.getExpiresInSeconds()).isEqualTo(900);
    }

    @Test
    void login_shouldThrowInvalidCredentials_whenEmailNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_shouldThrowInvalidCredentials_whenPasswordWrong() {
        User user = activeUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrongPassword")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_shouldThrowAccountLocked_whenUserLocked() {
        User user = activeUser();
        user.setStatus(UserStatus.LOCKED);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plainPassword", "hashedPassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "plainPassword")))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void refresh_shouldThrowInvalidRefreshToken_whenNotFound() {
        when(jwtService.hashToken("raw-token")).thenReturn("hashed");
        when(refreshTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_shouldThrowInvalidRefreshToken_whenRevoked() {
        User user = activeUser();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setRevoked(true);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtService.hashToken("raw-token")).thenReturn("hashed");
        when(refreshTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_shouldThrowInvalidRefreshToken_whenExpired() {
        User user = activeUser();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setRevoked(false);
        token.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(jwtService.hashToken("raw-token")).thenReturn("hashed");
        when(refreshTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_shouldReturnNewTokens_whenValid() {
        User user = activeUser();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setRevoked(false);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtService.hashToken("raw-token")).thenReturn("hashed");
        when(refreshTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(token));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
        when(jwtService.generateRawRefreshToken()).thenReturn("new-raw-refresh-token");
        when(jwtService.hashToken("new-raw-refresh-token")).thenReturn("new-hashed-refresh-token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        LoginResponse response = authService.refresh("raw-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-raw-refresh-token");
        assertThat(token.getRevoked()).isTrue();
    }

    @Test
    void logout_shouldRevokeToken_whenFound() {
        RefreshToken token = new RefreshToken();
        token.setRevoked(false);

        when(jwtService.hashToken("raw-token")).thenReturn("hashed");
        when(refreshTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(token));

        authService.logout("raw-token");

        assertThat(token.getRevoked()).isTrue();
    }

    @Test
    void logout_shouldNotThrow_whenTokenNotFound() {
        when(jwtService.hashToken("raw-token")).thenReturn("hashed");
        when(refreshTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.empty());

        authService.logout("raw-token");
    }

    @Test
    void forgotPassword_shouldSaveResetToken_whenEmailExists() {
        User user = activeUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateRawRefreshToken()).thenReturn("raw-reset-token");
        when(jwtService.hashToken("raw-reset-token")).thenReturn("hashed-reset-token");

        authService.forgotPassword("user@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hashed-reset-token");
        assertThat(captor.getValue().getUsed()).isFalse();
    }

    @Test
    void forgotPassword_shouldDoNothing_whenEmailNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("missing@example.com");

        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_shouldUpdatePasswordAndRevokeSessions_whenTokenValid() {
        User user = activeUser();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setUsed(false);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(jwtService.hashToken("raw-reset-token")).thenReturn("hashed-reset-token");
        when(passwordResetTokenRepository.findByTokenHash("hashed-reset-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newPassword")).thenReturn("newHashedPassword");

        authService.resetPassword("raw-reset-token", "newPassword");

        assertThat(user.getPasswordHash()).isEqualTo("newHashedPassword");
        assertThat(resetToken.getUsed()).isTrue();
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }

    @Test
    void resetPassword_shouldThrow_whenTokenNotFound() {
        when(jwtService.hashToken("bad-token")).thenReturn("hashed");
        when(passwordResetTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "newPassword"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPassword_shouldThrow_whenTokenAlreadyUsed() {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(activeUser());
        resetToken.setUsed(true);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(jwtService.hashToken("used-token")).thenReturn("hashed");
        when(passwordResetTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword("used-token", "newPassword"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPassword_shouldThrow_whenTokenExpired() {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(activeUser());
        resetToken.setUsed(false);
        resetToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(jwtService.hashToken("expired-token")).thenReturn("hashed");
        when(passwordResetTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "newPassword"))
                .isInstanceOf(InvalidResetTokenException.class);
    }
}
