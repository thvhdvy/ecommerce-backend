package com.thanhnguyen.ecommercebackend.payment.controller;

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
class PaymentControllerIntegrationTest {

    // Doc truc tiep tu config dang chay (khong hardcode) — test luon ky dung du secret la CHANGE_ME hay that.
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
        registerRequest.setFullName("Payment Test User");

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
        String adminToken = registerAndLogin("payment-admin-" + suffix + "@example.com", UserRole.ADMIN);

        MvcResult categoryResult = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Category-" + suffix, null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String sellerToken = registerAndLogin("payment-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
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
    void createIntent_thenSuccessfulIpn_shouldConfirmOrder() throws Exception {
        Long productId = createProductWithStock("intent1", new BigDecimal("15.00"), 10);
        String customerToken = registerAndLogin("payment-customer1@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(customerToken, productId, 3); // total = 45.00

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId", is(orderId.intValue())))
                .andReturn();
        String paymentUrl = objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText();
        String txnRef = extractTxnRef(paymentUrl);

        String ipnQuery = buildSignedIpnQuery(txnRef, "IPN-TXN-1", "00", "4500");
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        mockMvc.perform(get("/api/payments/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUCCEEDED")));

        // Order da CONFIRMED -> khong the tao payment intent moi
        mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("PAYMENT_NOT_ALLOWED")));
    }

    @Test
    void failedIpn_shouldMarkOrderPaymentFailed_andAllowRetry() throws Exception {
        Long productId = createProductWithStock("intent2", new BigDecimal("10.00"), 10);
        String customerToken = registerAndLogin("payment-customer2@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(customerToken, productId, 2); // total = 20.00

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());

        String ipnQuery = buildSignedIpnQuery(txnRef, "IPN-TXN-2", "24", "2000");
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PAYMENT_FAILED")));

        // Retry: goi lai /intent phai dua order ve PENDING_PAYMENT
        mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_PAYMENT")));
    }

    @Test
    void duplicateIpn_shouldNotDoubleProcess() throws Exception {
        Long productId = createProductWithStock("intent3", new BigDecimal("5.00"), 10);
        String customerToken = registerAndLogin("payment-customer3@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(customerToken, productId, 1); // total = 5.00

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());

        String ipnQuery = buildSignedIpnQuery(txnRef, "IPN-TXN-3", "00", "500");
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));
        // Gui IPN trung lan 2 — theo Flow 3, khong duoc xu ly thanh toan 2 lan
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("02")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));
    }

    @Test
    void createIntent_shouldReturn403_whenNotOwner() throws Exception {
        Long productId = createProductWithStock("intent4", new BigDecimal("10.00"), 10);
        String ownerToken = registerAndLogin("payment-owner4@example.com", UserRole.CUSTOMER);
        String otherToken = registerAndLogin("payment-other4@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(ownerToken, productId, 1);

        mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ORDER_OWNERSHIP_VIOLATION")));
    }

    @Test
    void amountMismatchIpn_shouldBeRejected_andListedForAdmin() throws Exception {
        Long productId = createProductWithStock("intent5", new BigDecimal("10.00"), 10);
        String customerToken = registerAndLogin("payment-customer5@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(customerToken, productId, 1); // total = 10.00

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());

        // VNPay bao ve 5.00 trong khi order can 10.00 — chu ky van hop le (chi so tien lech)
        String ipnQuery = buildSignedIpnQuery(txnRef, "IPN-MISMATCH-1", "00", "500");
        mockMvc.perform(get("/api/payments/vnpay-ipn?" + ipnQuery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("04")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_PAYMENT")));

        String adminToken = registerAndLogin("payment-admin-mismatch1@example.com", UserRole.ADMIN);
        mockMvc.perform(get("/api/admin/payments/amount-mismatches")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.vnpTransactionNo == 'IPN-MISMATCH-1')]").isNotEmpty());
    }

    @Test
    void validIpn_shouldStillConfirm_afterAmountMismatchWithSameTransactionNo() throws Exception {
        Long productId = createProductWithStock("intent7", new BigDecimal("10.00"), 10);
        String customerToken = registerAndLogin("payment-customer7@example.com", UserRole.CUSTOMER);
        Long orderId = checkoutOrder(customerToken, productId, 1); // total = 10.00

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());

        // Lan 1: amount lech -> tu choi, ghi AMOUNT_MISMATCH voi transaction no nay
        mockMvc.perform(get("/api/payments/vnpay-ipn?"
                        + buildSignedIpnQuery(txnRef, "IPN-MISMATCH-2", "00", "500")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("04")));

        // Lan 2: IPN hop le, CUNG transaction no — truoc V19 se bi record AMOUNT_MISMATCH "chiem cho"
        // idempotency key va bi coi la da xu ly (payment ket PENDING vinh vien); sau V19 phai confirm.
        mockMvc.perform(get("/api/payments/vnpay-ipn?"
                        + buildSignedIpnQuery(txnRef, "IPN-MISMATCH-2", "00", "1000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode", is("00")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));
    }

    @Test
    void amountMismatches_shouldReturn403_forNonAdmin() throws Exception {
        String customerToken = registerAndLogin("payment-customer6@example.com", UserRole.CUSTOMER);

        mockMvc.perform(get("/api/admin/payments/amount-mismatches")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }
}
