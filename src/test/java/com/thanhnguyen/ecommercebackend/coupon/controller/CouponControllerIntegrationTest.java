package com.thanhnguyen.ecommercebackend.coupon.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponCreateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponValidateRequest;
import com.thanhnguyen.ecommercebackend.coupon.entity.CouponDiscountType;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CouponControllerIntegrationTest {

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
        registerRequest.setFullName("Coupon Test User");

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

    private Long createProductWithStock(String suffix, BigDecimal price, int stock) throws Exception {
        String adminToken = registerAndLogin("coupon-admin-" + suffix + "@example.com", UserRole.ADMIN);

        MvcResult categoryResult = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Category-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String sellerToken = registerAndLogin("coupon-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
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
        Long productId = objectMapper.readTree(productResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(patch("/api/seller/products/" + productId + "/inventory")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateInventoryRequest(stock))))
                .andExpect(status().isOk());

        return productId;
    }

    private String createCoupon(String code, CouponDiscountType type, BigDecimal value, Integer usageLimit) throws Exception {
        String adminToken = registerAndLogin("coupon-admin-owner-" + code + "@example.com", UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponCreateRequest(
                                code, type, value, null, BigDecimal.ZERO, usageLimit, null, null))))
                .andExpect(status().isCreated());
        return adminToken;
    }

    private CheckoutRequest checkoutRequestWithCoupon(String couponCode) {
        return new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null, couponCode);
    }

    @Test
    void validate_shouldReturnDiscountPreview_withoutAuth() throws Exception {
        createCoupon("PREVIEW10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null);

        mockMvc.perform(post("/api/coupons/validate")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CouponValidateRequest("PREVIEW10", new BigDecimal("100.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountAmount", is(10.00)))
                .andExpect(jsonPath("$.data.finalTotal", is(90.00)));
    }

    @Test
    void checkout_shouldApplyDiscount_whenCouponValid() throws Exception {
        Long productId = createProductWithStock("apply1", new BigDecimal("50.00"), 10);
        createCoupon("SAVE20", CouponDiscountType.FIXED_AMOUNT, new BigDecimal("20.00"), null);
        String customerToken = registerAndLogin("coupon-customer-apply1@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequestWithCoupon("SAVE20"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount", is(30.00)))
                .andExpect(jsonPath("$.data.discountAmount", is(20.00)))
                .andExpect(jsonPath("$.data.couponCode", is("SAVE20")));
    }

    @Test
    void checkout_shouldReject_whenCouponUsageLimitExceeded() throws Exception {
        Long productId = createProductWithStock("limit1", new BigDecimal("50.00"), 10);
        createCoupon("ONLYONE", CouponDiscountType.FIXED_AMOUNT, new BigDecimal("5.00"), 1);

        String firstCustomer = registerAndLogin("coupon-customer-limit1a@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + firstCustomer)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + firstCustomer)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequestWithCoupon("ONLYONE"))))
                .andExpect(status().isCreated());

        String secondCustomer = registerAndLogin("coupon-customer-limit1b@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + secondCustomer)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + secondCustomer)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequestWithCoupon("ONLYONE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("COUPON_USAGE_LIMIT_EXCEEDED")));

        // Checkout that bai -> khong tao order dang do (design doc: "checkout that bai giua chung
        // -> khong tao order o trang thai do dang"). Gio hang cua secondCustomer van con nguyen.
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + secondCustomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()", is(1)));
    }

    @Test
    void checkout_shouldReject_whenSameUserReusesCoupon() throws Exception {
        Long productId = createProductWithStock("reuse1", new BigDecimal("50.00"), 10);
        createCoupon("ONCEEACH", CouponDiscountType.FIXED_AMOUNT, new BigDecimal("5.00"), null);
        String customerToken = registerAndLogin("coupon-customer-reuse1@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequestWithCoupon("ONCEEACH"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequestWithCoupon("ONCEEACH"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("COUPON_ALREADY_USED")));
    }

    @Test
    void checkout_shouldReject_whenOrderBelowMinOrderAmount() throws Exception {
        Long productId = createProductWithStock("minorder1", new BigDecimal("10.00"), 10);
        String adminToken = registerAndLogin("coupon-admin-owner-minorder1@example.com", UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponCreateRequest(
                                "BIGORDER", CouponDiscountType.FIXED_AMOUNT, new BigDecimal("5.00"), null,
                                new BigDecimal("1000.00"), null, null, null))))
                .andExpect(status().isCreated());

        String customerToken = registerAndLogin("coupon-customer-minorder1@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequestWithCoupon("BIGORDER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("COUPON_MIN_ORDER_NOT_MET")));
    }

    @Test
    void cancel_shouldReleaseCouponUsage_allowingReuseByAnotherOrder() throws Exception {
        Long productId = createProductWithStock("release1", new BigDecimal("50.00"), 10);
        createCoupon("RELEASEME", CouponDiscountType.FIXED_AMOUNT, new BigDecimal("5.00"), 1);
        String customerToken = registerAndLogin("coupon-customer-release1@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequestWithCoupon("RELEASEME"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Huy truoc khi thanh toan -> tra lai luot dung (design doc v2 muc 6.2).
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        String anotherCustomer = registerAndLogin("coupon-customer-release1b@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + anotherCustomer)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + anotherCustomer)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequestWithCoupon("RELEASEME"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.discountAmount", is(5.00)));
    }
}
