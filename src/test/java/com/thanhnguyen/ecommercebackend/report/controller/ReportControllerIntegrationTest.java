package com.thanhnguyen.ecommercebackend.report.controller;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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
 * revenue-by-day gom du lieu theo NGAY (khong tach theo test), nen cac assertion tren no dung ky thuat
 * "baseline truoc/sau" (delta) de khong bi anh huong boi order cua test khac chay CONFIRMED cung ngay.
 * revenue-by-category va top-products group theo category_id/product_id moi tao rieng cho tung test
 * nen assert duoc gia tri tuyet doi an toan.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerIntegrationTest {

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
        registerRequest.setFullName("Report Test User");

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

    private Long createCategory(String adminToken, String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Category-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private Long createProductInCategory(Long categoryId, String suffix, BigDecimal price, int stock) throws Exception {
        String sellerToken = registerAndLogin("report-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
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
                                new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null))))
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

        String ipnQuery = buildSignedIpnQuery(txnRef, "IPN-REPORT-" + orderId, "00", vnpAmount);
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

    private record DaySnapshot(BigDecimal gross, BigDecimal refund, BigDecimal net) {
    }

    private DaySnapshot revenueForToday(String adminToken, LocalDate today) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports/revenue-by-day")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        for (JsonNode node : data) {
            if (today.toString().equals(node.get("day").asText())) {
                return new DaySnapshot(
                        new BigDecimal(node.get("grossRevenue").asText()),
                        new BigDecimal(node.get("refundAmount").asText()),
                        new BigDecimal(node.get("netRevenue").asText()));
            }
        }
        return new DaySnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void revenueByDay_shouldIncreaseGrossAndNetByOrderAmount_whenNoRefund() throws Exception {
        String adminToken = registerAndLogin("report-admin-day1@example.com", UserRole.ADMIN);
        LocalDate today = LocalDate.now();
        DaySnapshot before = revenueForToday(adminToken, today);

        Long categoryId = createCategory(adminToken, "day1");
        Long productId = createProductInCategory(categoryId, "day1", new BigDecimal("30.00"), 5);
        String customerToken = registerAndLogin("report-customer-day1@example.com", UserRole.CUSTOMER);
        confirmOrderViaVnpay(customerToken, productId, 1, "3000");

        DaySnapshot after = revenueForToday(adminToken, today);
        assertThat(after.gross().subtract(before.gross())).isEqualByComparingTo("30.00");
        assertThat(after.net().subtract(before.net())).isEqualByComparingTo("30.00");
        assertThat(after.refund().subtract(before.refund())).isEqualByComparingTo("0.00");
    }

    @Test
    void revenueByDay_shouldNetOutRefund_whenOrderCancelledAfterConfirm() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-REFUND-REPORT-1"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        String adminToken = registerAndLogin("report-admin-day2@example.com", UserRole.ADMIN);
        LocalDate today = LocalDate.now();
        DaySnapshot before = revenueForToday(adminToken, today);

        Long categoryId = createCategory(adminToken, "day2");
        Long productId = createProductInCategory(categoryId, "day2", new BigDecimal("40.00"), 5);
        String customerToken = registerAndLogin("report-customer-day2@example.com", UserRole.CUSTOMER);
        Long orderId = confirmOrderViaVnpay(customerToken, productId, 1, "4000");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        DaySnapshot after = revenueForToday(adminToken, today);
        assertThat(after.gross().subtract(before.gross())).isEqualByComparingTo("40.00");
        assertThat(after.refund().subtract(before.refund())).isEqualByComparingTo("40.00");
        assertThat(after.net().subtract(before.net())).isEqualByComparingTo("0.00");
    }

    @Test
    void revenueByCategory_shouldSumGrossAcrossProductsInSameCategory() throws Exception {
        String adminToken = registerAndLogin("report-admin-cat1@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "cat1");
        Long productId1 = createProductInCategory(categoryId, "cat1a", new BigDecimal("10.00"), 5);
        Long productId2 = createProductInCategory(categoryId, "cat1b", new BigDecimal("20.00"), 5);

        String customer1 = registerAndLogin("report-customer-cat1a@example.com", UserRole.CUSTOMER);
        confirmOrderViaVnpay(customer1, productId1, 1, "1000");
        String customer2 = registerAndLogin("report-customer-cat1b@example.com", UserRole.CUSTOMER);
        confirmOrderViaVnpay(customer2, productId2, 1, "2000");

        MvcResult result = mockMvc.perform(get("/api/admin/reports/revenue-by-category")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");

        JsonNode entry = null;
        for (JsonNode node : data) {
            if (node.get("categoryId").asLong() == categoryId) {
                entry = node;
                break;
            }
        }
        assertThat(entry).isNotNull();
        assertThat(entry.get("categoryName").asText()).isEqualTo("Category-cat1");
        assertThat(new BigDecimal(entry.get("grossRevenue").asText())).isEqualByComparingTo("30.00");
    }

    @Test
    void topProducts_shouldRankByGrossRevenueDescending() throws Exception {
        String adminToken = registerAndLogin("report-admin-top1@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "top1");
        Long highRevenueProductId = createProductInCategory(categoryId, "top1-high", new BigDecimal("500.00"), 5);
        Long lowRevenueProductId = createProductInCategory(categoryId, "top1-low", new BigDecimal("5.00"), 5);

        String customer1 = registerAndLogin("report-customer-top1a@example.com", UserRole.CUSTOMER);
        confirmOrderViaVnpay(customer1, highRevenueProductId, 1, "50000");
        String customer2 = registerAndLogin("report-customer-top1b@example.com", UserRole.CUSTOMER);
        confirmOrderViaVnpay(customer2, lowRevenueProductId, 1, "500");

        MvcResult result = mockMvc.perform(get("/api/admin/reports/top-products")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");

        int highIndex = -1;
        int lowIndex = -1;
        for (int i = 0; i < data.size(); i++) {
            long productId = data.get(i).get("productId").asLong();
            if (productId == highRevenueProductId) {
                highIndex = i;
                assertThat(new BigDecimal(data.get(i).get("grossRevenue").asText())).isEqualByComparingTo("500.00");
                assertThat(data.get(i).get("quantitySold").asLong()).isEqualTo(1L);
            }
            if (productId == lowRevenueProductId) {
                lowIndex = i;
            }
        }
        assertThat(highIndex).isGreaterThanOrEqualTo(0);
        assertThat(lowIndex).isGreaterThanOrEqualTo(0);
        assertThat(highIndex).isLessThan(lowIndex);
    }

    @Test
    void reports_shouldReturn403_forNonAdmin() throws Exception {
        String customerToken = registerAndLogin("report-nonadmin1@example.com", UserRole.CUSTOMER);
        mockMvc.perform(get("/api/admin/reports/revenue-by-day")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void topProducts_shouldClampToMinimumOne_whenLimitIsNegative() throws Exception {
        String adminToken = registerAndLogin("report-admin-limit1@example.com", UserRole.ADMIN);
        mockMvc.perform(get("/api/admin/reports/top-products")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("limit", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", org.hamcrest.Matchers.lessThanOrEqualTo(1)));
    }

    @Test
    void revenueByDay_shouldReturn400_whenFromIsNotAValidDate() throws Exception {
        String adminToken = registerAndLogin("report-admin-baddate1@example.com", UserRole.ADMIN);
        mockMvc.perform(get("/api/admin/reports/revenue-by-day")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_PARAMETER")));
    }
}
