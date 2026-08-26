package com.thanhnguyen.ecommercebackend.shipping.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayClient;
import com.thanhnguyen.ecommercebackend.payment.util.VnpayRefundResult;
import com.thanhnguyen.ecommercebackend.product.dto.CategoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.shipping.dto.AssignShipperRequest;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryFailureReason;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ShippingControllerIntegrationTest {

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
        registerRequest.setFullName("Shipping Test User");

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
        String sellerToken = registerAndLogin("shipping-seller-" + suffix + "@example.com", UserRole.CUSTOMER);
        mockMvc.perform(post("/api/sellers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BecomeSellerRequest("Shop-" + suffix, null))))
                .andExpect(status().isCreated());
        return sellerToken;
    }

    private Long createProductWithStock(String sellerToken, String suffix, BigDecimal price, int stock) throws Exception {
        String adminToken = registerAndLogin("shipping-admin-" + suffix + "@example.com", UserRole.ADMIN);

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

    /** Checkout -> intent -> IPN thanh cong (ky that) -> order CONFIRMED -> seller pack -> order PACKED. Tra ve orderId. */
    private Long buildPackedOrder(String customerToken, String sellerToken, Long productId, int quantity, String vnpAmount)
            throws Exception {
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
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        MvcResult intentResult = mockMvc.perform(post("/api/payments/" + orderId + "/intent")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String txnRef = extractTxnRef(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("data").get("paymentUrl").asText());
        String ipnQuery = buildSignedIpnQuery(txnRef, "SHIP-IPN-" + orderId, "00", vnpAmount);
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

        return orderId;
    }

    private String assignShipper(String adminToken, Long orderId, Long shipperId) throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(shipperId))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
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
    void assignShipper_shouldMoveOrderToShipped_whenOrderPacked() throws Exception {
        String sellerToken = becomeSeller("assign1");
        Long productId = createProductWithStock(sellerToken, "assign1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-assign1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        String shipperToken = registerAndLogin("shipping-shipper-assign1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("shipping-shipper-assign1@example.com").orElseThrow();
        String adminToken = registerAndLogin("shipping-admin-assign-action1@example.com", UserRole.ADMIN);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(shipper.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.data.shipperId", is(shipper.getId().intValue())));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SHIPPED")));

        mockMvc.perform(get("/api/shipper/deliveries")
                        .header("Authorization", "Bearer " + shipperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].orderId", is(orderId.intValue())));
    }

    @Test
    void assignShipper_shouldReturn409_whenOrderNotPackedYet() throws Exception {
        String sellerToken = becomeSeller("notpacked1");
        Long productId = createProductWithStock(sellerToken, "notpacked1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-notpacked1@example.com", UserRole.CUSTOMER);

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

        String shipperToken = registerAndLogin("shipping-shipper-notpacked1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("shipping-shipper-notpacked1@example.com").orElseThrow();
        String adminToken = registerAndLogin("shipping-admin-notpacked-action1@example.com", UserRole.ADMIN);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(shipper.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("DELIVERY_NOT_ALLOWED")));

        mockMvc.perform(get("/api/shipper/deliveries")
                        .header("Authorization", "Bearer " + shipperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));
    }

    @Test
    void updateStatus_delivered_shouldMoveOrderToDelivered() throws Exception {
        String sellerToken = becomeSeller("deliver1");
        Long productId = createProductWithStock(sellerToken, "deliver1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-deliver1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        String shipperToken = registerAndLogin("shipping-shipper-deliver1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("shipping-shipper-deliver1@example.com").orElseThrow();
        String adminToken = registerAndLogin("shipping-admin-deliver-action1@example.com", UserRole.ADMIN);
        String deliveryId = assignShipper(adminToken, orderId, shipper.getId());

        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + shipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryStatusUpdateRequest(DeliveryStatus.DELIVERED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELIVERED")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELIVERED")));
    }

    @Test
    void updateStatus_failedFirstTime_shouldAutoRetry_andKeepOrderShipped() throws Exception {
        String sellerToken = becomeSeller("retry1");
        Long productId = createProductWithStock(sellerToken, "retry1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-retry1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        String shipperToken = registerAndLogin("shipping-shipper-retry1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("shipping-shipper-retry1@example.com").orElseThrow();
        String adminToken = registerAndLogin("shipping-admin-retry-action1@example.com", UserRole.ADMIN);
        String deliveryId = assignShipper(adminToken, orderId, shipper.getId());

        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + shipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeliveryStatusUpdateRequest(DeliveryStatus.FAILED, DeliveryFailureReason.CUSTOMER_UNREACHABLE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.data.retryCount", is(1)));

        // Order van SHIPPED (dang retry, chua huy)
        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SHIPPED")));

        // Retry thanh cong lan nay
        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + shipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryStatusUpdateRequest(DeliveryStatus.DELIVERED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELIVERED")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELIVERED")));
    }

    @Test
    void updateStatus_failedSecondTime_shouldAutoCancelOrder_andTriggerRefund() throws Exception {
        doReturn(VnpayRefundResult.success("VNP-REFUND-SHIP-1"))
                .when(vnpayClient)
                .requestRefund(anyString(), any(), any(LocalDateTime.class), any(BigDecimal.class), anyString(), anyString());

        String sellerToken = becomeSeller("cancel1");
        Long productId = createProductWithStock(sellerToken, "cancel1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-cancel1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        String shipperToken = registerAndLogin("shipping-shipper-cancel1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("shipping-shipper-cancel1@example.com").orElseThrow();
        String adminToken = registerAndLogin("shipping-admin-cancel-action1@example.com", UserRole.ADMIN);
        String deliveryId = assignShipper(adminToken, orderId, shipper.getId());

        // That bai lan 1 -> auto retry
        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + shipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeliveryStatusUpdateRequest(DeliveryStatus.FAILED, DeliveryFailureReason.CUSTOMER_UNREACHABLE))))
                .andExpect(status().isOk());

        // That bai lan 2 -> het quyen retry -> auto-cancel + refund
        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + shipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeliveryStatusUpdateRequest(DeliveryStatus.FAILED, DeliveryFailureReason.CUSTOMER_REJECTED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("FAILED")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));
    }

    @Test
    void updateStatus_shouldReturn403_whenNotOwningShipper() throws Exception {
        String sellerToken = becomeSeller("owner1");
        Long productId = createProductWithStock(sellerToken, "owner1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-owner1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        registerAndLogin("shipping-shipper-owner1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("shipping-shipper-owner1@example.com").orElseThrow();
        String otherShipperToken = registerAndLogin("shipping-shipper-owner1other@example.com", UserRole.SHIPPER);
        String adminToken = registerAndLogin("shipping-admin-owner-action1@example.com", UserRole.ADMIN);
        String deliveryId = assignShipper(adminToken, orderId, shipper.getId());

        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + otherShipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryStatusUpdateRequest(DeliveryStatus.IN_TRANSIT, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("DELIVERY_OWNERSHIP_VIOLATION")));
    }

    @Test
    void assignShipper_shouldReassignToNewShipper_whenOrderAlreadyShipped() throws Exception {
        String sellerToken = becomeSeller("reassign1");
        Long productId = createProductWithStock(sellerToken, "reassign1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-reassign1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        String oldShipperToken = registerAndLogin("shipping-shipper-reassign1-old@example.com", UserRole.SHIPPER);
        User oldShipper = userRepository.findByEmail("shipping-shipper-reassign1-old@example.com").orElseThrow();
        String newShipperToken = registerAndLogin("shipping-shipper-reassign1-new@example.com", UserRole.SHIPPER);
        User newShipper = userRepository.findByEmail("shipping-shipper-reassign1-new@example.com").orElseThrow();
        String adminToken = registerAndLogin("shipping-admin-reassign-action1@example.com", UserRole.ADMIN);

        // Gan shipper lan dau -> order chuyen SHIPPED
        assignShipper(adminToken, orderId, oldShipper.getId());
        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SHIPPED")));

        // Shipper cu unavailable -> admin gan lai shipper moi trong khi order van SHIPPED
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(newShipper.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.data.shipperId", is(newShipper.getId().intValue())));

        // Order status khong doi (van SHIPPED, khong tao them delivery moi)
        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SHIPPED")));

        // Shipper cu khong con thay delivery nay, shipper moi thay
        mockMvc.perform(get("/api/shipper/deliveries")
                        .header("Authorization", "Bearer " + oldShipperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));
        mockMvc.perform(get("/api/shipper/deliveries")
                        .header("Authorization", "Bearer " + newShipperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].orderId", is(orderId.intValue())))
                .andExpect(jsonPath("$.data[0].status", is("ASSIGNED")));
    }

    @Test
    void confirmDelivery_shouldMoveOrderToDelivered_whenAdminConfirmsManually() throws Exception {
        String sellerToken = becomeSeller("confirm1");
        Long productId = createProductWithStock(sellerToken, "confirm1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-confirm1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        String shipperToken = registerAndLogin("shipping-shipper-confirm1@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("shipping-shipper-confirm1@example.com").orElseThrow();
        String adminToken = registerAndLogin("shipping-admin-confirm-action1@example.com", UserRole.ADMIN);
        assignShipper(adminToken, orderId, shipper.getId());

        // Shipper da giao thanh cong nhung app crash, khong gui duoc status update len he thong.
        // Admin doi soat qua bao cao ngoai he thong roi xac nhan thu cong.
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/confirm-delivery")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELIVERED")));

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("DELIVERED")));

        mockMvc.perform(get("/api/shipper/deliveries")
                        .header("Authorization", "Bearer " + shipperToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status", is("DELIVERED")));
    }

    @Test
    void confirmDelivery_shouldReturn409_whenDeliveryAlreadyDelivered() throws Exception {
        String sellerToken = becomeSeller("confirm2");
        Long productId = createProductWithStock(sellerToken, "confirm2", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-confirm2@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        String shipperToken = registerAndLogin("shipping-shipper-confirm2@example.com", UserRole.SHIPPER);
        User shipper = userRepository.findByEmail("shipping-shipper-confirm2@example.com").orElseThrow();
        String adminToken = registerAndLogin("shipping-admin-confirm-action2@example.com", UserRole.ADMIN);
        String deliveryId = assignShipper(adminToken, orderId, shipper.getId());

        mockMvc.perform(patch("/api/shipper/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + shipperToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryStatusUpdateRequest(DeliveryStatus.DELIVERED, null))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/confirm-delivery")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("DELIVERY_NOT_ALLOWED")));
    }

    @Test
    void confirmDelivery_shouldReturn403_forNonAdmin() throws Exception {
        String sellerToken = becomeSeller("confirm3");
        Long productId = createProductWithStock(sellerToken, "confirm3", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-confirm3@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/confirm-delivery")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignShipper_shouldReturn409_whenTargetUserNotShipper() throws Exception {
        String sellerToken = becomeSeller("notshipper1");
        Long productId = createProductWithStock(sellerToken, "notshipper1", new BigDecimal("10.00"), 5);
        String customerToken = registerAndLogin("shipping-customer-notshipper1@example.com", UserRole.CUSTOMER);
        Long orderId = buildPackedOrder(customerToken, sellerToken, productId, 1, "1000");

        String adminToken = registerAndLogin("shipping-admin-notshipper-action1@example.com", UserRole.ADMIN);
        User plainCustomer = userRepository.findByEmail("shipping-customer-notshipper1@example.com").orElseThrow();

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/assign-shipper")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignShipperRequest(plainCustomer.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("NOT_A_SHIPPER")));
    }
}
