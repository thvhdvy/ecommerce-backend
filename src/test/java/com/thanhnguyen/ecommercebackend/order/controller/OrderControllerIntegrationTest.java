package com.thanhnguyen.ecommercebackend.order.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.order.entity.OrderItemStatus;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayClient;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayRefundResult;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private VnpayClient vnpayClient;

    private String registerAndLogin(String email, UserRole role) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword("Password123");
        registerRequest.setFullName("Order Test User");

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
        String adminToken = registerAndLogin("order-admin-" + suffix + "@example.com", UserRole.ADMIN);

        MvcResult categoryResult = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Category-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String sellerToken = registerAndLogin("order-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
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

    private CheckoutRequest defaultCheckoutRequest() {
        return new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null, null);
    }

    /** Tao seller + product + stock, tra ve [sellerToken, sellerId, productId] — dung cho test nhieu seller. */
    private Object[] createSellerWithProduct(String suffix, BigDecimal price, int stock) throws Exception {
        String sellerToken = registerAndLogin("order-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
        MvcResult sellerResult = mockMvc.perform(post("/api/sellers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BecomeSellerRequest("Shop-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long sellerId = objectMapper.readTree(sellerResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String adminToken = registerAndLogin("order-admin-" + suffix + "@example.com", UserRole.ADMIN);
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

        return new Object[]{sellerToken, sellerId, productId};
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

    /** Checkout toan bo gio hang hien tai -> intent -> IPN thanh cong (ky that) -> order CONFIRMED. */
    private Long confirmCartViaVnpay(String customerToken, String vnpAmount) throws Exception {
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultCheckoutRequest())))
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

        String ipnQuery = buildSignedIpnQuery(txnRef, "ORDER-CANCEL-IPN-" + orderId, "00", vnpAmount);
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));

        return orderId;
    }

    @Test
    void checkout_shouldCreateOrder_andEmptyCart() throws Exception {
        Long productId = createProductWithStock("checkout1", new BigDecimal("15.00"), 10);
        String customerToken = registerAndLogin("order-customer1@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 3))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultCheckoutRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PENDING_PAYMENT")))
                .andExpect(jsonPath("$.data.totalAmount", is(45.00)))
                .andExpect(jsonPath("$.data.items.length()", is(1)))
                .andExpect(jsonPath("$.data.items[0].quantity", is(3)));

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()", is(0)));
    }

    @Test
    void checkout_shouldReturn400_whenCartEmpty() throws Exception {
        String customerToken = registerAndLogin("order-customer2@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultCheckoutRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("EMPTY_CART")));
    }

    @Test
    void checkout_shouldReturn409_whenInsufficientStock() throws Exception {
        Long productId = createProductWithStock("checkout3", new BigDecimal("10.00"), 1);
        String customerToken = registerAndLogin("order-customer3@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 5))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultCheckoutRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("INSUFFICIENT_STOCK")));
    }

    @Test
    void getMyOrder_shouldReturn403_whenNotOwner() throws Exception {
        Long productId = createProductWithStock("checkout4", new BigDecimal("10.00"), 5);
        String ownerToken = registerAndLogin("order-owner4@example.com", UserRole.CUSTOMER);
        String otherToken = registerAndLogin("order-other4@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());

        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultCheckoutRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ORDER_OWNERSHIP_VIOLATION")));
    }

    @Test
    void cancel_shouldReleaseInventory_andMarkCancelled() throws Exception {
        Long productId = createProductWithStock("checkout5", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("order-customer5@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 2))))
                .andExpect(status().isCreated());

        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultCheckoutRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ORDER_CANCEL_NOT_ALLOWED")));
    }

    @Test
    void cancelSellerItems_shouldOnlyAffectThatSeller_whenOrderHasMultipleSellers() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-CUSTOMER-SELLER-CANCEL-1"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Object[] sellerAInfo = createSellerWithProduct("multisel-a", new BigDecimal("20.00"), 5);
        Long sellerAId = (Long) sellerAInfo[1];
        Long productAId = (Long) sellerAInfo[2];
        Object[] sellerBInfo = createSellerWithProduct("multisel-b", new BigDecimal("30.00"), 5);
        Long sellerBId = (Long) sellerBInfo[1];
        Long productBId = (Long) sellerBInfo[2];

        String customerToken = registerAndLogin("order-customer-multisel@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productAId, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productBId, 1))))
                .andExpect(status().isCreated());

        // Order tong 50.00 (20 + 30), 2 seller doc lap trong cung 1 order.
        Long orderId = confirmCartViaVnpay(customerToken, "5000");

        // Chi huy phan cua seller A — seller B chua dung gi toi.
        MvcResult cancelResult = mockMvc.perform(post("/api/orders/" + orderId + "/sellers/" + sellerAId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();

        // Design doc v2 muc 10.5: huy 1 phan -> order KHONG chuyen CANCELLED toan bo, van phan anh
        // tien do cua seller con lai (aggregate-min) — o day seller B chua PACKED nen van CONFIRMED.
        JsonNode data = objectMapper.readTree(cancelResult.getResponse().getContentAsString()).get("data");
        assertThat(data.get("status").asText()).isEqualTo("CONFIRMED");

        boolean sellerAItemCancelled = false;
        boolean sellerBItemStillActive = false;
        for (JsonNode item : data.get("items")) {
            if (item.get("sellerId").asLong() == sellerAId) {
                sellerAItemCancelled = item.get("itemStatus").asText().equals(OrderItemStatus.CANCELLED.name());
            }
            if (item.get("sellerId").asLong() == sellerBId) {
                sellerBItemStillActive = !item.get("itemStatus").asText().equals(OrderItemStatus.CANCELLED.name());
            }
        }
        assertThat(sellerAItemCancelled).isTrue();
        assertThat(sellerBItemStillActive).isTrue();

        // Refund chi tinh phan seller A (20.00), khong phai toan order (50.00).
        verify(vnpayClient).requestRefund(
                anyString(), any(), any(LocalDateTime.class), eq(new BigDecimal("20.00")), anyString(), anyString());

        // Huy lan 2 cho chinh seller A -> 409, vi item da CANCELLED tu truoc (idempotent guard).
        mockMvc.perform(post("/api/orders/" + orderId + "/sellers/" + sellerAId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ORDER_CANCEL_NOT_ALLOWED")));
    }

    @Test
    void cancelSellerItems_shouldReturn403_whenNotOwner() throws Exception {
        Object[] sellerInfo = createSellerWithProduct("selperm", new BigDecimal("10.00"), 5);
        Long sellerId = (Long) sellerInfo[1];
        Long productId = (Long) sellerInfo[2];

        String ownerToken = registerAndLogin("order-owner-selperm@example.com", UserRole.CUSTOMER);
        String otherToken = registerAndLogin("order-other-selperm@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        Long orderId = confirmCartViaVnpay(ownerToken, "1000");

        mockMvc.perform(post("/api/orders/" + orderId + "/sellers/" + sellerId + "/cancel")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ORDER_OWNERSHIP_VIOLATION")));

        verify(vnpayClient, never()).requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void listMyOrders_shouldReturnOnlyOwnOrders() throws Exception {
        Long productId = createProductWithStock("checkout6", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("order-customer6@example.com", UserRole.CUSTOMER);
        String otherToken = registerAndLogin("order-other6@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultCheckoutRequest())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(0)))
                .andExpect(jsonPath("$.data.totalElements", is(0)));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(1)))
                .andExpect(jsonPath("$.data.totalElements", is(1)));
    }
}
