package com.thanhnguyen.ecommercebackend.review.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.product.dto.CategoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.review.dto.CreateReviewRequest;
import com.thanhnguyen.ecommercebackend.shipping.dto.AssignShipperRequest;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatus;
import com.thanhnguyen.ecommercebackend.user.dto.BecomeSellerRequest;
import com.thanhnguyen.ecommercebackend.user.dto.LoginRequest;
import com.thanhnguyen.ecommercebackend.user.dto.RegisterRequest;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserRole;
import com.thanhnguyen.ecommercebackend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
class ReviewControllerIntegrationTest {

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

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
        registerRequest.setFullName("Review Test User");

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

    private String becomeSeller(String suffix) throws Exception {
        String sellerToken = registerAndLogin("review-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/sellers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BecomeSellerRequest("Shop-" + suffix, null))))
                .andExpect(status().isCreated());
        return sellerToken;
    }

    private Long createProductWithStock(String sellerToken, String suffix, BigDecimal price, int stock) throws Exception {
        String adminToken = registerAndLogin("review-admin-" + suffix + "@example.com", UserRole.ADMIN);

        MvcResult categoryResult = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Category-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

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

    /** Checkout -> intent -> IPN (ky that) -> CONFIRMED -> seller pack -> PACKED -> assign shipper -> SHIPPED -> DELIVERED. */
    private Long buildDeliveredOrder(String customerToken, String sellerToken, Long productId, String suffix) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());

        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());
        String ipnQuery = buildSignedIpnQuery(txnRef, "REVIEW-IPN-" + suffix, "00", "1000");
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));

        MvcResult sellerOrders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andReturn();
        Long orderItemId = objectMapper.readTree(sellerOrders.getResponse().getContentAsString())
                .get("data").get(0).get("items").get(0).get("id").asLong();
        mockMvc.perform(patch("/api/seller/order-items/" + orderItemId + "/status")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        String shipperToken = registerAndLogin("review-shipper-" + suffix + "@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("review-shipper-" + suffix + "@example.com").orElseThrow();
        String adminActionToken = registerAndLogin("review-admin-action-" + suffix + "@example.com", UserRole.ADMIN);

        MvcResult assignResult = mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminActionToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(shipper.getId()))))
                .andExpect(status().isOk())
                .andReturn();
        Long deliveryId = objectMapper.readTree(assignResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + shipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryStatusUpdateRequest(DeliveryStatus.DELIVERED, null))))
                .andExpect(status().isOk());

        return orderId;
    }

    private String extractTxnRef(String paymentUrl) {
        Matcher matcher = Pattern.compile("vnp_TxnRef=([^&]+)").matcher(paymentUrl);
        if (!matcher.find()) {
            throw new IllegalStateException("vnp_TxnRef not found in paymentUrl: " + paymentUrl);
        }
        return matcher.group(1);
    }

    private String buildSignedIpnQuery(String txnRef, String transactionNo, String responseCode, String amount) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_TransactionNo", transactionNo);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_Amount", amount);

        StringBuilder query = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }
        String secureHash = hmacSHA512(hashSecret, query.toString());
        return query + "&vnp_SecureHash=" + secureHash;
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void create_shouldSucceed_andUpdateProductRating_whenOrderDelivered() throws Exception {
        String sellerToken = becomeSeller("ok1");
        Long productId = createProductWithStock(sellerToken, "ok1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("review-customer-ok1@example.com", UserRole.CUSTOMER);
        buildDeliveredOrder(customerToken, sellerToken, productId, "ok1");

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "Great product"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating", is(5)))
                .andExpect(jsonPath("$.data.status", is("VISIBLE")));

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ratingAvg", is(5.0)));
    }

    @Test
    void create_shouldReturn403_whenNeverPurchased() throws Exception {
        String sellerToken = becomeSeller("noeat1");
        Long productId = createProductWithStock(sellerToken, "noeat1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("review-customer-noeat1@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(4, "Nice"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("REVIEW_NOT_ELIGIBLE")));
    }

    @Test
    void create_shouldReturn403_whenOrderNotDeliveredYet() throws Exception {
        String sellerToken = becomeSeller("pending1");
        Long productId = createProductWithStock(sellerToken, "pending1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("review-customer-pending1@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(4, "Nice"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("REVIEW_NOT_ELIGIBLE")));
    }

    @Test
    void create_shouldReturn409_whenAlreadyReviewed() throws Exception {
        String sellerToken = becomeSeller("dup1");
        Long productId = createProductWithStock(sellerToken, "dup1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("review-customer-dup1@example.com", UserRole.CUSTOMER);
        buildDeliveredOrder(customerToken, sellerToken, productId, "dup1");

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "Great"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(1, "Changed my mind"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("REVIEW_ALREADY_EXISTS")));
    }

    @Test
    void create_shouldReturn400_whenRatingOutOfRange() throws Exception {
        String sellerToken = becomeSeller("badrating1");
        Long productId = createProductWithStock(sellerToken, "badrating1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("review-customer-badrating1@example.com", UserRole.CUSTOMER);
        buildDeliveredOrder(customerToken, sellerToken, productId, "badrating1");

        mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(6, "Too high"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    @Test
    void listByProduct_shouldExcludeHiddenReviews() throws Exception {
        String sellerToken = becomeSeller("hide1");
        Long productId = createProductWithStock(sellerToken, "hide1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("review-customer-hide1@example.com", UserRole.CUSTOMER);
        buildDeliveredOrder(customerToken, sellerToken, productId, "hide1");

        MvcResult createResult = mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(1, "Bad experience"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long reviewId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)));

        String adminToken = registerAndLogin("review-admin-hide-action1@example.com", UserRole.ADMIN);
        mockMvc.perform(patch("/api/admin/reviews/" + reviewId + "/hide")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("HIDDEN")));

        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ratingAvg", is(0.0)));
    }

    @Test
    void adminHide_shouldReturn403_forNonAdmin() throws Exception {
        String sellerToken = becomeSeller("perm1");
        Long productId = createProductWithStock(sellerToken, "perm1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("review-customer-perm1@example.com", UserRole.CUSTOMER);
        buildDeliveredOrder(customerToken, sellerToken, productId, "perm1");

        MvcResult createResult = mockMvc.perform(post("/api/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(3, "Ok"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long reviewId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(patch("/api/admin/reviews/" + reviewId + "/hide")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }
}
