package com.thanhnguyen.ecommercebackend.cart.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.cart.dto.UpdateCartItemRequest;
import com.thanhnguyen.ecommercebackend.product.dto.CategoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.user.dto.BecomeSellerRequest;
import com.thanhnguyen.ecommercebackend.user.dto.LoginRequest;
import com.thanhnguyen.ecommercebackend.user.dto.RegisterRequest;
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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CartControllerIntegrationTest {

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
        registerRequest.setFullName("Cart Test User");

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

    private Long createProduct(String suffix, BigDecimal price) throws Exception {
        String adminToken = registerAndLogin("cart-admin-" + suffix + "@example.com", UserRole.ADMIN);

        MvcResult categoryResult = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Category-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String sellerToken = registerAndLogin("cart-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/sellers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BecomeSellerRequest("Shop-" + suffix, null))))
                .andExpect(status().isCreated());

        MvcResult productResult = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCreateRequest(
                                "Product-" + suffix, "desc", price, categoryId, null, null))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(productResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    @Test
    void getCart_shouldReturnEmpty_whenNoCartYet() throws Exception {
        String customerToken = registerAndLogin("cart-empty@example.com", UserRole.CUSTOMER);

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()", is(0)))
                .andExpect(jsonPath("$.data.totalAmount", is(0)));
    }

    @Test
    void addItem_shouldCreateCartLazily_andReturnItem() throws Exception {
        Long productId = createProduct("add1", new BigDecimal("10.00"));
        String customerToken = registerAndLogin("cart-add1@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()", is(1)))
                .andExpect(jsonPath("$.data.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.data.totalAmount", is(20.00)));
    }

    @Test
    void addItem_shouldMergeQuantity_whenProductAlreadyInCart() throws Exception {
        Long productId = createProduct("add2", new BigDecimal("5.00"));
        String customerToken = registerAndLogin("cart-add2@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()", is(1)))
                .andExpect(jsonPath("$.data.items[0].quantity", is(4)));
    }

    @Test
    void addItem_shouldReturn404_whenProductNotFound() throws Exception {
        String customerToken = registerAndLogin("cart-add3@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(999999L, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
    }

    @Test
    void updateItem_shouldUpdateQuantity() throws Exception {
        Long productId = createProduct("update1", new BigDecimal("7.00"));
        String customerToken = registerAndLogin("cart-update1@example.com", UserRole.CUSTOMER);

        MvcResult addResult = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated())
                .andReturn();
        Long itemId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("data").get("items").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/cart/items/" + itemId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity", is(5)));
    }

    @Test
    void updateItem_shouldReturn404_whenNotOwner() throws Exception {
        Long productId = createProduct("update2", new BigDecimal("7.00"));
        String ownerToken = registerAndLogin("cart-update2-owner@example.com", UserRole.CUSTOMER);
        String otherToken = registerAndLogin("cart-update2-other@example.com", UserRole.CUSTOMER);

        MvcResult addResult = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated())
                .andReturn();
        Long itemId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("data").get("items").get(0).get("id").asLong();

        // Item ton tai nhung thuoc cart nguoi khac -> 403 ownership (thong nhat voi Product/Order,
        // design doc Flow 6), khong con 404 nhu truoc
        mockMvc.perform(patch("/api/cart/items/" + itemId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(2))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CART_OWNERSHIP_VIOLATION")));
    }

    @Test
    void removeItem_shouldRemoveItem() throws Exception {
        Long productId = createProduct("remove1", new BigDecimal("3.00"));
        String customerToken = registerAndLogin("cart-remove1@example.com", UserRole.CUSTOMER);

        MvcResult addResult = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated())
                .andReturn();
        Long itemId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("data").get("items").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/cart/items/" + itemId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()", is(0)));
    }

    @Test
    void cartEndpoints_shouldReturn401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }
}
