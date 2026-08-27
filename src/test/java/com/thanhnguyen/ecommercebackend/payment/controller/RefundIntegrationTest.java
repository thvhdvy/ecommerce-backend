package com.thanhnguyen.ecommercebackend.payment.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VnpayClient duoc spy (khong mock toan bo) de /intent va IPN van dung logic ky/verify HMAC that —
 * chi requestRefund() duoc stub de tranh goi mang that ra VNPay sandbox trong test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RefundIntegrationTest {

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
        registerRequest.setFullName("Refund Test User");

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
        String adminToken = registerAndLogin("refund-admin-" + suffix + "@example.com", UserRole.ADMIN);

        MvcResult categoryResult = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Category-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String sellerToken = registerAndLogin("refund-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
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

    private Long checkoutOrder(String customerToken, Long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, quantity))))
                .andExpect(status().isCreated());

        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    /** Checkout -> tao payment intent -> gui IPN thanh cong (ky that) -> order ve CONFIRMED. */
    private Long confirmOrderViaVnpay(String customerToken, Long productId, int quantity, String vnpAmount) throws Exception {
        Long orderId = checkoutOrder(customerToken, productId, quantity);

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());

        String ipnQuery = buildSignedIpnQuery(txnRef, "IPN-CONFIRM-" + orderId, "00", vnpAmount);
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));

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
    void customerCancel_shouldTriggerRefund_whenOrderConfirmed() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-REFUND-1"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Long productId = createProductWithStock("cancel1", new BigDecimal("20.00"), 10);
        String customerToken = registerAndLogin("refund-customer1@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 2, "4000"); // 20.00 * 2 = 40.00

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        verify(vnpayClient).requestRefund(
                anyString(), any(), any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(new BigDecimal("40.00")),
                anyString(), anyString());
    }

    @Test
    void adminRetryRefund_shouldReturn403_forNonAdmin() throws Exception {
        Long productId = createProductWithStock("retry1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("refund-customer2@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 1, "1000");

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund/retry")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRetryRefund_shouldSucceed_forAdmin() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-REFUND-2"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Long productId = createProductWithStock("retry2", new BigDecimal("15.00"), 5);
        String customerToken = registerAndLogin("refund-customer3@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 1, "1500");

        String adminToken = registerAndLogin("refund-admin-retryaction2@example.com", UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/refund/retry")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        verify(vnpayClient).requestRefund(
                anyString(), any(), any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(new BigDecimal("15.00")),
                anyString(), anyString());
    }

    @Test
    void adminListFailedRefunds_shouldIncludeRefund_whenVnpayRefundFails() throws Exception {
        doReturn(VnpayRefundResult.failure("91"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Long productId = createProductWithStock("failed1", new BigDecimal("12.00"), 5);
        String customerToken = registerAndLogin("refund-customer4@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 1, "1200");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        String adminToken = registerAndLogin("refund-admin-list1@example.com", UserRole.ADMIN);
        mockMvc.perform(get("/api/admin/refunds/failed")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.orderId == " + orderId + ")].status", is(java.util.List.of("REFUND_FAILED"))));
    }

    @Test
    void adminResolveRefundManually_shouldMarkResolved_andRemoveFromFailedList() throws Exception {
        doReturn(VnpayRefundResult.failure("91"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Long productId = createProductWithStock("failed2", new BigDecimal("18.00"), 5);
        String customerToken = registerAndLogin("refund-customer5@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 1, "1800");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        String adminToken = registerAndLogin("refund-admin-resolve1@example.com", UserRole.ADMIN);
        MvcResult listResult = mockMvc.perform(get("/api/admin/refunds/failed")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode refunds = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data").get("content");
        long refundId = -1;
        for (JsonNode node : refunds) {
            if (node.get("orderId").asLong() == orderId) {
                refundId = node.get("id").asLong();
            }
        }

        mockMvc.perform(patch("/api/admin/refunds/" + refundId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"note\":\"Hoan tien tay qua chuyen khoan ngan hang\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/refunds/failed")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.orderId == " + orderId + ")]").isEmpty());

        // Da resolve roi thi khong duoc resolve lai lan nua
        mockMvc.perform(patch("/api/admin/refunds/" + refundId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"note\":\"resolve lan 2\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminResolveRefundManually_shouldReject_whenNoteBlank() throws Exception {
        doReturn(VnpayRefundResult.failure("91"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Long productId = createProductWithStock("failed3", new BigDecimal("9.00"), 5);
        String customerToken = registerAndLogin("refund-customer6@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 1, "900");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        String adminToken = registerAndLogin("refund-admin-blank1@example.com", UserRole.ADMIN);
        MvcResult listResult = mockMvc.perform(get("/api/admin/refunds/failed")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode refunds = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("data").get("content");
        long refundId = -1;
        for (JsonNode node : refunds) {
            if (node.get("orderId").asLong() == orderId) {
                refundId = node.get("id").asLong();
            }
        }

        mockMvc.perform(patch("/api/admin/refunds/" + refundId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"note\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminRefundEndpoints_shouldReturn403_forNonAdmin() throws Exception {
        String customerToken = registerAndLogin("refund-customer7@example.com", UserRole.CUSTOMER);

        mockMvc.perform(get("/api/admin/refunds/failed")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/refunds/1/resolve")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"note\":\"anything\"}"))
                .andExpect(status().isForbidden());
    }
}
