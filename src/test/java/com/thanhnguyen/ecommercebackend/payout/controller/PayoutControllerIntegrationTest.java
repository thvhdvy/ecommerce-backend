package com.thanhnguyen.ecommercebackend.payout.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayClient;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayRefundResult;
import com.thanhnguyen.ecommercebackend.product.dto.CategoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnCreateRequest;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnReason;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Driving 1 order het DELIVERED -> COMPLETED (bang cach backdate orders.updated_at qua JdbcTemplate
 * roi goi thang OrderService.autoCompleteDeliveredOrders(), vi scheduled job that can 3 ngay va
 * khong co endpoint "force complete" cho test) de verify PayoutService.recordEarning() duoc wire dung
 * (design doc v2 muc 9.3). VnpayClient duoc spy nhu ReturnControllerIntegrationTest.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PayoutControllerIntegrationTest {

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${payout.commission-rate:0.10}")
    private BigDecimal commissionRate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private VnpayClient vnpayClient;

    private String registerAndLogin(String email, UserRole role) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword("Password123");
        registerRequest.setFullName("Payout Test User");

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

    /** Tao seller, tra ve [sellerToken, sellerId]. */
    private Object[] becomeSeller(String suffix) throws Exception {
        String sellerToken = registerAndLogin("payout-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
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
        String adminToken = registerAndLogin("payout-admin-" + suffix + "@example.com", UserRole.ADMIN);

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

    /** Checkout -> intent -> IPN thanh cong -> CONFIRMED -> pack -> assign shipper -> SHIPPED -> DELIVERED. Tra ve [orderId, orderItemId, deliveryId]. */
    private long[] buildDeliveredOrder(String customerToken, String sellerToken, String adminToken, Long shipperId,
                                        Long productId, int quantity, String vnpAmount) throws Exception {
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
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());
        String ipnQuery = buildSignedIpnQuery(txnRef, "PAYOUT-IPN-" + orderId, "00", vnpAmount);
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));

        MvcResult sellerOrders = mockMvc.perform(get("/api/seller/orders")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ordersContent = objectMapper.readTree(sellerOrders.getResponse().getContentAsString())
                .get("data").get("content");
        Long orderItemId = null;
        for (JsonNode order : ordersContent) {
            if (order.get("id").asLong() == orderId) {
                orderItemId = order.get("items").get(0).get("id").asLong();
                break;
            }
        }

        mockMvc.perform(patch("/api/seller/order-items/" + orderItemId + "/status")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        MvcResult assignResult = mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(shipperId))))
                .andExpect(status().isOk())
                .andReturn();
        String deliveryId = objectMapper.readTree(assignResult.getResponse().getContentAsString())
                .get("data").get("id").asText();

        return new long[]{orderId, orderItemId, Long.parseLong(deliveryId)};
    }

    private void markDelivered(String shipperToken, long deliveryId) throws Exception {
        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + shipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryStatusUpdateRequest(DeliveryStatus.DELIVERED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELIVERED")));
    }

    @Test
    void orderCompleted_shouldCreditSellerBalance_andAllowAdminPayOut() throws Exception {
        Object[] sellerInfo = becomeSeller("earn1");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "earn1", new BigDecimal("50.00"), 10);
        String customerToken = registerAndLogin("payout-customer-earn1@example.com", UserRole.CUSTOMER);
        String shipperToken = registerAndLogin("payout-shipper-earn1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("payout-shipper-earn1@example.com").orElseThrow();
        String adminToken = registerAndLogin("payout-admin-action-earn1@example.com", UserRole.ADMIN);

        long[] ids = buildDeliveredOrder(customerToken, sellerToken, adminToken, shipper.getId(), productId, 2, "10000");
        markDelivered(shipperToken, ids[2]);

        // Backdate qua raw SQL (khong qua JPA save() de tranh @PreUpdate ghi de lai now()) de
        // scheduled job auto-complete coi day la don da qua han 3 ngay.
        jdbcTemplate.update("UPDATE orders SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(4), ids[0]);
        orderService.autoCompleteDeliveredOrders();

        BigDecimal gross = new BigDecimal("100.00"); // 50.00 * 2
        BigDecimal commission = gross.multiply(commissionRate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(commission);

        mockMvc.perform(get("/api/admin/sellers/" + sellerId + "/balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance", is(net.doubleValue())));

        mockMvc.perform(get("/api/seller/ledger")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type", is("EARNED")))
                .andExpect(jsonPath("$.data.content[0].netAmount", is(net.doubleValue())));

        mockMvc.perform(post("/api/admin/sellers/" + sellerId + "/payouts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount", is(net.doubleValue())));

        mockMvc.perform(get("/api/admin/sellers/" + sellerId + "/balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance", is(0.00)));

        mockMvc.perform(get("/api/seller/payouts")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].amount", is(net.doubleValue())));
    }

    @Test
    void payOut_shouldReturn409_whenBalanceNotPositive() throws Exception {
        Object[] sellerInfo = becomeSeller("nobalance1");
        Long sellerId = (Long) sellerInfo[1];
        String adminToken = registerAndLogin("payout-admin-action-nobalance1@example.com", UserRole.ADMIN);

        mockMvc.perform(post("/api/admin/sellers/" + sellerId + "/payouts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("PAYOUT_NOT_ALLOWED")));
    }

    @Test
    void returnRefunded_beforeOrderCompleted_shouldMakeSellerBalanceNegative() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-PAYOUT-ADJ-1"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        Object[] sellerInfo = becomeSeller("adj1");
        String sellerToken = (String) sellerInfo[0];
        Long sellerId = (Long) sellerInfo[1];
        Long productId = createProductWithStock(sellerToken, "adj1", new BigDecimal("40.00"), 5);
        String customerToken = registerAndLogin("payout-customer-adj1@example.com", UserRole.CUSTOMER);
        String shipperToken = registerAndLogin("payout-shipper-adj1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("payout-shipper-adj1@example.com").orElseThrow();
        String adminToken = registerAndLogin("payout-admin-action-adj1@example.com", UserRole.ADMIN);

        long[] ids = buildDeliveredOrder(customerToken, sellerToken, adminToken, shipper.getId(), productId, 1, "4000");
        markDelivered(shipperToken, ids[2]);
        // KHONG auto-complete — order van con DELIVERED, seller_balances chua co dong EARNED nao.

        MvcResult createResult = mockMvc.perform(post("/api/returns")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReturnCreateRequest(ids[1], ReturnReason.DEFECTIVE, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long returnId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(patch("/api/seller/returns/" + returnId + "/approve")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/seller/returns/" + returnId + "/item-received")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        // Seller chua tung EARNED nhung da bi tru 40.00 do return -> balance am (design doc v2 muc 9.4).
        mockMvc.perform(get("/api/admin/sellers/" + sellerId + "/balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance", is(-40.00)));
    }
}
