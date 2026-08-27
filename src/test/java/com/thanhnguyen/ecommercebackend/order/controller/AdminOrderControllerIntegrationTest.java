package com.thanhnguyen.ecommercebackend.order.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayClient;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayRefundResult;
import com.thanhnguyen.ecommercebackend.product.dto.CategoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.shipping.dto.AssignShipperRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin force-cancel: gap tim thay khi audit design doc (0.6 "PACKED tro di... chi ADMIN force-cancel").
 * VnpayClient duoc spy, chi stub requestRefund() de tranh goi mang that ra VNPay sandbox trong test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminOrderControllerIntegrationTest {

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.thanhnguyen.ecommercebackend.order.repository.OrderStatusHistoryRepository orderStatusHistoryRepository;

    @MockitoSpyBean
    private VnpayClient vnpayClient;

    private String registerAndLogin(String email, UserRole role) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword("Password123");
        registerRequest.setFullName("Admin Order Test User");

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
        return ((String) becomeSellerWithId(suffix)[0]);
    }

    /** Tao seller, tra ve [sellerToken, sellerId] — dung khi test can sellerId (VD assign-shipper). */
    private Object[] becomeSellerWithId(String suffix) throws Exception {
        String sellerToken = registerAndLogin("admin-order-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
        MvcResult result = mockMvc.perform(post("/api/sellers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BecomeSellerRequest("Shop-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long sellerId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        return new Object[]{sellerToken, sellerId};
    }

    private Long createProductWithStock(String sellerToken, String suffix, BigDecimal price, int stock) throws Exception {
        String adminToken = registerAndLogin("admin-order-admin-" + suffix + "@example.com", UserRole.ADMIN);

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

    /** Checkout -> intent -> IPN thanh cong (ky that) -> order CONFIRMED. */
    private Long confirmOrderViaVnpay(String customerToken, Long productId, int quantity, String vnpAmount) throws Exception {
        Long orderId = checkoutOrder(customerToken, productId, quantity);

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());

        String ipnQuery = buildSignedIpnQuery(txnRef, "ADMIN-CANCEL-IPN-" + orderId, "00", vnpAmount);
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));

        return orderId;
    }

    /** CONFIRMED -> seller pack het item -> order PACKED. */
    private Long buildPackedOrder(String customerToken, String sellerToken, Long productId, int quantity, String vnpAmount)
            throws Exception {
        Long orderId = confirmOrderViaVnpay(customerToken, productId, quantity, vnpAmount);

        MvcResult sellerOrders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andReturn();
        Long orderItemId = objectMapper.readTree(sellerOrders.getResponse().getContentAsString())
                .get("data").get("content").get(0).get("items").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/seller/order-items/" + orderItemId + "/status")
                        .header("Authorization", "Bearer " + sellerToken))
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
    void listAll_shouldReturn403_forNonAdmin() throws Exception {
        Long productId = createProductWithStock(becomeSeller("listperm1"), "listperm1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-listperm1@example.com", UserRole.CUSTOMER);
        checkoutOrder(customerToken, productId, 1);

        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAll_shouldReturnOrders_forAdmin() throws Exception {
        Long productId = createProductWithStock(becomeSeller("list1"), "list1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-list1@example.com", UserRole.CUSTOMER);
        checkoutOrder(customerToken, productId, 1);

        String adminToken = registerAndLogin("admin-order-actor-list1@example.com", UserRole.ADMIN);
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void forceCancel_shouldReturn403_forNonAdmin() throws Exception {
        Long productId = createProductWithStock(becomeSeller("perm1"), "perm1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-perm1@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(customerToken, productId, 1);

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        verify(vnpayClient, never()).requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void forceCancel_shouldCancel_withoutRefund_whenPendingPayment() throws Exception {
        Long productId = createProductWithStock(becomeSeller("pending1"), "pending1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-pending1@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(customerToken, productId, 2); // giu 2/5 tồn kho

        String adminToken = registerAndLogin("admin-order-actor-pending1@example.com", UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        verify(vnpayClient, never()).requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        // History row phai dung: from PENDING_PAYMENT, to CANCELLED, changed_by = admin, co reason.
        Long adminUserId = userRepository.findByEmail("admin-order-actor-pending1@example.com").orElseThrow().getId();
        var historyRows = orderStatusHistoryRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId);
        org.assertj.core.api.Assertions.assertThat(historyRows).hasSize(2); // dong PENDING_PAYMENT ban dau + dong CANCELLED
        var latest = historyRows.get(0);
        org.assertj.core.api.Assertions.assertThat(latest.getFromStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        org.assertj.core.api.Assertions.assertThat(latest.getToStatus()).isEqualTo(OrderStatus.CANCELLED);
        // getId() tren lazy proxy khong trigger load (id da co san tren proxy) — an toan ngoai transaction.
        org.assertj.core.api.Assertions.assertThat(latest.getChangedBy().getId()).isEqualTo(adminUserId);
        org.assertj.core.api.Assertions.assertThat(latest.getReason()).isEqualTo("Admin force-cancelled order before payment");

        // Reserved stock phai duoc release: mua lai du 5/5 (2 da huy + 3 con lai) phai thanh cong.
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 5))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null, null))))
                .andExpect(status().isCreated());
    }

    @Test
    void forceCancel_shouldReturn404_whenOrderNotFound() throws Exception {
        String adminToken = registerAndLogin("admin-order-actor-notfound1@example.com", UserRole.ADMIN);

        mockMvc.perform(post("/api/admin/orders/999999/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("ORDER_NOT_FOUND")));
    }

    @Test
    void forceCancel_shouldCancelAndRefund_whenOrderConfirmed() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-ADMIN-CANCEL-1"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Object[] sellerInfo = becomeSellerWithId("confirmed1");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "confirmed1", new BigDecimal("20.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-confirmed1@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 2, "4000"); // 20.00 * 2 = 40.00

        String adminToken = registerAndLogin("admin-order-actor-confirmed1@example.com", UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/sellers/" + sellerId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        verify(vnpayClient).requestRefund(
                anyString(), any(), any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(new BigDecimal("40.00")),
                anyString(), anyString());
    }

    @Test
    void forceCancel_shouldCancelAndRefund_whenOrderPacked() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-ADMIN-CANCEL-2"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Object[] sellerInfo = becomeSellerWithId("packed1");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "packed1", new BigDecimal("15.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-packed1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1500");

        String adminToken = registerAndLogin("admin-order-actor-packed1@example.com", UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/sellers/" + sellerId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        verify(vnpayClient).requestRefund(
                anyString(), any(), any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(new BigDecimal("15.00")),
                anyString(), anyString());
    }

    @Test
    void forceCancel_shouldReturn409_whenOrderAlreadyShipped() throws Exception {
        Object[] sellerInfo = becomeSellerWithId("shipped1");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "shipped1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-shipped1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        registerAndLogin("admin-order-shipper-shipped1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("admin-order-shipper-shipped1@example.com").orElseThrow();
        String adminToken = registerAndLogin("admin-order-actor-shipped1@example.com", UserRole.ADMIN);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(sellerId, shipper.getId()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ORDER_CANCEL_NOT_ALLOWED")));

        verify(vnpayClient, never()).requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void forceCancel_shouldReturn409_whenAlreadyCancelled() throws Exception {
        Long productId = createProductWithStock(becomeSeller("dup1"), "dup1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-dup1@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(customerToken, productId, 1);

        String adminToken = registerAndLogin("admin-order-actor-dup1@example.com", UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ORDER_CANCEL_NOT_ALLOWED")));
    }

    @Test
    void cancelSellerItems_shouldSucceedAndRefund_whenSellerNotYetPacked() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-SELLER-CANCEL-1"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Object[] sellerInfo = becomeSellerWithId("selcancel1");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "selcancel1", new BigDecimal("25.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-selcancel1@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 2, "5000"); // 25.00 * 2 = 50.00

        mockMvc.perform(post("/api/orders/" + orderId + "/sellers/" + sellerId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        verify(vnpayClient).requestRefund(
                anyString(), any(), any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(new BigDecimal("50.00")),
                anyString(), anyString());
    }

    @Test
    void cancelSellerItems_shouldReturn409_whenSellerAlreadyPacked() throws Exception {
        Object[] sellerInfo = becomeSellerWithId("selcancel2");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "selcancel2", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-selcancel2@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        mockMvc.perform(post("/api/orders/" + orderId + "/sellers/" + sellerId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ORDER_CANCEL_NOT_ALLOWED")));

        verify(vnpayClient, never()).requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void forceCancelSellerItems_shouldSucceedAndRefund_whenSellerAlreadyPacked() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-SELLER-CANCEL-2"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Object[] sellerInfo = becomeSellerWithId("selcancel3");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "selcancel3", new BigDecimal("12.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-selcancel3@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1200");

        String adminToken = registerAndLogin("admin-order-actor-selcancel3@example.com", UserRole.ADMIN);
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/sellers/" + sellerId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        verify(vnpayClient).requestRefund(
                anyString(), any(), any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(new BigDecimal("12.00")),
                anyString(), anyString());
    }

    @Test
    void forceCancelSellerItems_shouldReturn409_whenSellerAlreadyHasDelivery() throws Exception {
        Object[] sellerInfo = becomeSellerWithId("selcancel4");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "selcancel4", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("admin-order-customer-selcancel4@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        registerAndLogin("admin-order-shipper-selcancel4@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("admin-order-shipper-selcancel4@example.com").orElseThrow();
        String adminToken = registerAndLogin("admin-order-actor-selcancel4@example.com", UserRole.ADMIN);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(sellerId, shipper.getId()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/sellers/" + sellerId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ORDER_CANCEL_NOT_ALLOWED")));

        verify(vnpayClient, never()).requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());
    }
}
