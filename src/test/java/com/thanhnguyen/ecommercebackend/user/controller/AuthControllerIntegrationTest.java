package com.thanhnguyen.ecommercebackend.user.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.security.JwtService;
import com.thanhnguyen.ecommercebackend.user.dto.ForgotPasswordRequest;
import com.thanhnguyen.ecommercebackend.user.dto.LoginRequest;
import com.thanhnguyen.ecommercebackend.user.dto.RefreshRequest;
import com.thanhnguyen.ecommercebackend.user.dto.RegisterRequest;
import com.thanhnguyen.ecommercebackend.user.dto.ResetPasswordRequest;
import com.thanhnguyen.ecommercebackend.user.entity.PasswordResetToken;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserStatus;
import com.thanhnguyen.ecommercebackend.user.repository.PasswordResetTokenRepository;
import com.thanhnguyen.ecommercebackend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void register_shouldReturn201_whenRequestValid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("integration@example.com");
        request.setPassword("Password123");
        request.setFullName("Integration Test");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is("integration@example.com")))
                .andExpect(jsonPath("$.data.role", is("CUSTOMER")));
    }

    @Test
    void register_shouldReturn400_whenEmailInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("Password123");
        request.setFullName("Bad Email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void register_shouldReturn400_whenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("shortpass@example.com");
        request.setPassword("123");
        request.setFullName("Short Pass");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void register_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("duplicate@example.com");
        request.setPassword("Password123");
        request.setFullName("First User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")));
    }

    private void register(String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setFullName("Login Test User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    @Test
    void login_shouldReturnTokens_whenCredentialsValid() throws Exception {
        register("login-ok@example.com", "Password123");

        JsonNode data = login("login-ok@example.com", "Password123");

        org.assertj.core.api.Assertions.assertThat(data.get("accessToken").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(data.get("refreshToken").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(data.get("tokenType").asText()).isEqualTo("Bearer");
    }

    @Test
    void login_shouldReturn401_whenPasswordWrong() throws Exception {
        register("login-badpw@example.com", "Password123");

        LoginRequest request = new LoginRequest("login-badpw@example.com", "WrongPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void login_shouldReturn403_whenAccountLocked() throws Exception {
        register("login-locked@example.com", "Password123");
        User user = userRepository.findByEmail("login-locked@example.com").orElseThrow();
        user.setStatus(UserStatus.LOCKED);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("login-locked@example.com", "Password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_LOCKED")));
    }

    @Test
    void refresh_shouldReturnNewTokens_thenRejectReuseOfOldToken() throws Exception {
        register("refresh-ok@example.com", "Password123");
        JsonNode loginData = login("refresh-ok@example.com", "Password123");
        String oldRefreshToken = loginData.get("refreshToken").asText();

        RefreshRequest refreshRequest = new RefreshRequest(oldRefreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // token rotation: the old refresh token must no longer be usable
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void logout_shouldRevokeToken_soSubsequentRefreshFails() throws Exception {
        register("logout-ok@example.com", "Password123");
        JsonNode loginData = login("logout-ok@example.com", "Password123");
        String refreshToken = loginData.get("refreshToken").asText();

        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void forgotPassword_shouldReturn200_regardlessOfWhetherEmailExists() throws Exception {
        register("forgot-exists@example.com", "Password123");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("forgot-exists@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("no-such-user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void resetPassword_shouldReturn401_whenTokenInvalid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("bogus-token", "NewPassword123");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_RESET_TOKEN")));
    }

    @Test
    void resetPassword_shouldAllowLoginWithNewPassword_whenTokenValid() throws Exception {
        register("reset-ok@example.com", "OldPassword123");
        User user = userRepository.findByEmail("reset-ok@example.com").orElseThrow();

        String rawToken = "known-raw-reset-token";
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(jwtService.hashToken(rawToken));
        resetToken.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(30));
        resetToken.setUsed(false);
        passwordResetTokenRepository.save(resetToken);

        ResetPasswordRequest request = new ResetPasswordRequest(rawToken, "NewPassword123");
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // old password no longer works
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("reset-ok@example.com", "OldPassword123"))))
                .andExpect(status().isUnauthorized());

        // new password works
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("reset-ok@example.com", "NewPassword123"))))
                .andExpect(status().isOk());

        // reusing the same reset token again must fail
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_RESET_TOKEN")));
    }
}
