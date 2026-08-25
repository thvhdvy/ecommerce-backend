package com.thanhnguyen.ecommercebackend.user.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.user.dto.LoginRequest;
import com.thanhnguyen.ecommercebackend.user.dto.RegisterRequest;
import com.thanhnguyen.ecommercebackend.user.dto.UserLockRequest;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserRole;
import com.thanhnguyen.ecommercebackend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String registerAndLogin(String email, UserRole role) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword("Password123");
        registerRequest.setFullName("Admin User Test");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        if (role != UserRole.CUSTOMER) {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.setRole(role);
            userRepository.save(user);
        }

        LoginRequest loginRequest = new LoginRequest(email, "Password123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("accessToken").asText();
    }

    private String extractRefreshToken(String email) throws Exception {
        LoginRequest loginRequest = new LoginRequest(email, "Password123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("refreshToken").asText();
    }

    @Test
    void listAll_shouldReturn403_forNonAdmin() throws Exception {
        String customerToken = registerAndLogin("adminuser-customer-perm1@example.com", UserRole.CUSTOMER);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAll_shouldReturnAllUsers_forAdmin() throws Exception {
        registerAndLogin("adminuser-target-list1@example.com", UserRole.CUSTOMER);
        String adminToken = registerAndLogin("adminuser-admin-list1@example.com", UserRole.ADMIN);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void lock_shouldReturn409_whenAdminLocksOwnAccount() throws Exception {
        String adminToken = registerAndLogin("adminuser-admin-selflock1@example.com", UserRole.ADMIN);
        User admin = userRepository.findByEmail("adminuser-admin-selflock1@example.com").orElseThrow();

        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserLockRequest(true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("SELF_LOCK_NOT_ALLOWED")));

        // Admin van con hoat dong binh thuong sau khi bi tu choi tu-khoa.
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void lock_shouldRevokeRefreshToken_andBlockOldAccessToken_onNextRequest() throws Exception {
        String targetToken = registerAndLogin("adminuser-target-lock1@example.com", UserRole.CUSTOMER);
        String refreshToken = extractRefreshToken("adminuser-target-lock1@example.com");
        User target = userRepository.findByEmail("adminuser-target-lock1@example.com").orElseThrow();
        String adminToken = registerAndLogin("adminuser-admin-lock1@example.com", UserRole.ADMIN);

        // Truoc khi lock, access token con hieu luc.
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + target.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserLockRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("LOCKED")));

        // Access token cu bi tu choi ngay request ke tiep (JwtAuthenticationFilter re-check status).
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());

        // Refresh token cung bi revoke -> khong the lay access token moi.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // Dang nhap lai cung bi tu choi.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("adminuser-target-lock1@example.com", "Password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_LOCKED")));
    }

    @Test
    void unlock_shouldAllowLoginAgain() throws Exception {
        registerAndLogin("adminuser-target-unlock1@example.com", UserRole.CUSTOMER);
        User target = userRepository.findByEmail("adminuser-target-unlock1@example.com").orElseThrow();
        String adminToken = registerAndLogin("adminuser-admin-unlock1@example.com", UserRole.ADMIN);

        mockMvc.perform(patch("/api/admin/users/" + target.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserLockRequest(true))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + target.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserLockRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("adminuser-target-unlock1@example.com", "Password123"))))
                .andExpect(status().isOk());
    }
}
