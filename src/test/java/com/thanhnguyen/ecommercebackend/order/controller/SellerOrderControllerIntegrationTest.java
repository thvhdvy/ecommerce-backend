package com.thanhnguyen.ecommercebackend.order.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
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
class SellerOrderControllerIntegrationTest {

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
        registerRequest.setFullName("Seller Order Test User");

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
        String sellerToken = registerAndLogin("seller-order-" + suffix + "@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/sellers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BecomeSellerRequest("Shop-" + suffix, null))))
                .andExpect(status().isCreated());
        return sellerToken;
    }

    private Long createProductWithStock(String sellerToken, String suffix, BigDecimal price, int stock) throws Exception {
        String adminToken = registerAndLogin("seller-order-admin-" + suffix + "@example.com", UserRole.ADMIN);

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

    private Long addToCart(String customerToken, Long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, quantity))))
                .andExpect(status().isCreated());
        return productId;
    }

    private Long checkout(String customerToken) throws Exception {
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private void confirmOrderViaIpn(String customerToken, Long orderId, String txnSuffix, String vnpAmount) throws Exception {
        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String paymentUrl = objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText();
        String txnRef = extractTxnRef(paymentUrl);

        String ipnQuery = buildSignedIpnQuery(txnRef, "SELLER-IPN-" + txnSuffix, "00", vnpAmount);
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));
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
    void packOrderItem_shouldMoveOrderToPacked_whenSingleSellerAndAllItemsPacked() throws Exception {
        String sellerToken = becomeSeller("pack1");
        Long productId = createProductWithStock(sellerToken, "pack1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("seller-order-customer-pack1@example.com", UserRole.CUSTOMER);
        addToCart(customerToken, productId, 2);
        Long orderId = checkout(customerToken);
        confirmOrderViaIpn(customerToken, orderId, "pack1", "2000");

        MvcResult sellerOrders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andReturn();
        Long orderItemId = objectMapper.readTree(sellerOrders.getResponse().getContentAsString())
                .get("data").get(0).get("items").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/seller/order-items/" + orderItemId + "/status")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemStatus", is("PACKED")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PACKED")));
    }

    @Test
    void packOrderItem_shouldKeepOrderConfirmed_untilAllSellersPack() throws Exception {
        String seller1Token = becomeSeller("multi1a");
        String seller2Token = becomeSeller("multi1b");
        Long product1Id = createProductWithStock(seller1Token, "multi1a", new BigDecimal("10.00"), 5);
        Long product2Id = createProductWithStock(seller2Token, "multi1b", new BigDecimal("20.00"), 5);

        String customerToken = registerAndLogin("seller-order-customer-multi1@example.com", UserRole.CUSTOMER);
        addToCart(customerToken, product1Id, 1);
        addToCart(customerToken, product2Id, 1);
        Long orderId = checkout(customerToken);
        confirmOrderViaIpn(customerToken, orderId, "multi1", "3000");

        MvcResult seller1Orders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + seller1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].items.length()", is(1)))
                .andReturn();
        Long item1Id = objectMapper.readTree(seller1Orders.getResponse().getContentAsString())
                .get("data").get(0).get("items").get(0).get("id").asLong();

        MvcResult seller2Orders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + seller2Token))
                .andExpect(status().isOk())
                .andReturn();
        Long item2Id = objectMapper.readTree(seller2Orders.getResponse().getContentAsString())
                .get("data").get(0).get("items").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/seller/order-items/" + item1Id + "/status")
                        .header("Authorization", "Bearer " + seller1Token))
                .andExpect(status().isOk());

        // Chi 1/2 seller da pack -> order van CONFIRMED (aggregate rule)
        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        mockMvc.perform(patch("/api/seller/order-items/" + item2Id + "/status")
                        .header("Authorization", "Bearer " + seller2Token))
                .andExpect(status().isOk());

        // Seller cuoi cung pack xong -> order chuyen PACKED
        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PACKED")));
    }

    @Test
    void packOrderItem_shouldReturn403_whenNotOwningSeller() throws Exception {
        String sellerToken = becomeSeller("owner1");
        String otherSellerToken = becomeSeller("owner1other");
        Long productId = createProductWithStock(sellerToken, "owner1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("seller-order-customer-owner1@example.com", UserRole.CUSTOMER);
        addToCart(customerToken, productId, 1);
        Long orderId = checkout(customerToken);
        confirmOrderViaIpn(customerToken, orderId, "owner1", "1000");

        MvcResult sellerOrders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andReturn();
        Long orderItemId = objectMapper.readTree(sellerOrders.getResponse().getContentAsString())
                .get("data").get(0).get("items").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/seller/order-items/" + orderItemId + "/status")
                        .header("Authorization", "Bearer " + otherSellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ORDER_OWNERSHIP_VIOLATION")));
    }

    @Test
    void packOrderItem_shouldReturn409_whenOrderNotConfirmedYet() throws Exception {
        String sellerToken = becomeSeller("notconf1");
        Long productId = createProductWithStock(sellerToken, "notconf1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("seller-order-customer-notconf1@example.com", UserRole.CUSTOMER);
        addToCart(customerToken, productId, 1);
        Long orderId = checkout(customerToken); // van con PENDING_PAYMENT

        MvcResult sellerOrders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andReturn();
        Long orderItemId = objectMapper.readTree(sellerOrders.getResponse().getContentAsString())
                .get("data").get(0).get("items").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/seller/order-items/" + orderItemId + "/status")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ORDER_ITEM_STATUS_NOT_ALLOWED")));
    }

    @Test
    void packOrderItem_shouldReturn409_whenAlreadyPacked() throws Exception {
        String sellerToken = becomeSeller("dup1");
        Long productId = createProductWithStock(sellerToken, "dup1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("seller-order-customer-dup1@example.com", UserRole.CUSTOMER);
        addToCart(customerToken, productId, 1);
        Long orderId = checkout(customerToken);
        confirmOrderViaIpn(customerToken, orderId, "dup1", "1000");

        MvcResult sellerOrders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andReturn();
        Long orderItemId = objectMapper.readTree(sellerOrders.getResponse().getContentAsString())
                .get("data").get(0).get("items").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/seller/order-items/" + orderItemId + "/status")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/seller/order-items/" + orderItemId + "/status")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ORDER_ITEM_STATUS_NOT_ALLOWED")));
    }
}
