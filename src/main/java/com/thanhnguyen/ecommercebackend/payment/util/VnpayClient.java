package com.thanhnguyen.ecommercebackend.payment.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * VNPay không có SDK Java chính thức — request/verify được build thủ công theo spec HMAC-SHA512
 * (tài liệu: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
 */
@Component
@Slf4j
public class VnpayClient {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String VNP_VERSION = "2.1.0";

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String refundApiUrl;
    private final String returnUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VnpayClient(
            @Value("${vnpay.tmn-code}") String tmnCode,
            @Value("${vnpay.hash-secret}") String hashSecret,
            @Value("${vnpay.pay-url}") String payUrl,
            @Value("${vnpay.refund-api-url}") String refundApiUrl,
            @Value("${vnpay.return-url}") String returnUrl) {
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
        this.refundApiUrl = refundApiUrl;
        this.returnUrl = returnUrl;
    }

    public String generateTxnRef(Long orderId) {
        return orderId + "-" + System.currentTimeMillis();
    }

    public String buildPaymentUrl(String txnRef, BigDecimal amount, Long orderId, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VNP_VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", toVnpAmount(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + orderId);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", now.format(DATE_FORMAT));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(DATE_FORMAT));

        String query = buildEncodedQuery(params);
        String secureHash = hmacSHA512(hashSecret, query);
        return payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    /** Verify chữ ký của IPN/return params — không sửa map truyền vào. */
    public boolean verifySignature(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null) {
            return false;
        }

        Map<String, String> toVerify = new TreeMap<>(params);
        toVerify.remove("vnp_SecureHash");
        toVerify.remove("vnp_SecureHashType");

        String query = buildEncodedQuery(toVerify);
        String computedHash = hmacSHA512(hashSecret, query);
        return computedHash.equalsIgnoreCase(receivedHash);
    }

    /**
     * Gọi VNPay Refund API (server-to-server, JSON, phản hồi đồng bộ — khác webhook/IPN).
     * Cần vnp_TmnCode/hash-secret sandbox thật để test end-to-end.
     */
    public VnpayRefundResult requestRefund(
            String vnpTxnRef, String originalTransactionNo, LocalDateTime originalTransactionDate,
            BigDecimal amount, String reason, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String createDate = now.format(DATE_FORMAT);
        String vnpAmount = toVnpAmount(amount);
        String transactionType = "02"; // 02 = hoan tien toan phan, 03 = hoan tien mot phan
        String createBy = "admin";
        String transactionNo = originalTransactionNo != null ? originalTransactionNo : "0";
        String transactionDate = originalTransactionDate.format(DATE_FORMAT);

        String hashData = String.join("|",
                requestId, VNP_VERSION, "refund", tmnCode, transactionType, vnpTxnRef, vnpAmount,
                transactionNo, transactionDate, createBy, createDate, clientIp, reason);
        String secureHash = hmacSHA512(hashSecret, hashData);

        Map<String, Object> body = new TreeMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", VNP_VERSION);
        body.put("vnp_Command", "refund");
        body.put("vnp_TmnCode", tmnCode);
        body.put("vnp_TransactionType", transactionType);
        body.put("vnp_TxnRef", vnpTxnRef);
        body.put("vnp_Amount", vnpAmount);
        body.put("vnp_OrderInfo", reason);
        body.put("vnp_TransactionNo", transactionNo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateBy", createBy);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", clientIp);
        body.put("vnp_SecureHash", secureHash);

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(refundApiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode responseBody = objectMapper.readTree(response.body());
            String responseCode = responseBody.path("vnp_ResponseCode").asText();

            if ("00".equals(responseCode)) {
                return VnpayRefundResult.success(responseBody.path("vnp_TransactionNo").asText(null));
            }
            return VnpayRefundResult.failure(responseCode);
        } catch (Exception e) {
            log.error("VNPay refund API call failed for txnRef={}", vnpTxnRef, e);
            return VnpayRefundResult.failure("CONNECTION_ERROR");
        }
    }

    /**
     * Goi VNPay Query Transaction API (querydr) — merchant chu dong hoi trang thai giao dich thay vi
     * cho IPN push vao. Dung khi IPN bi tre/mat (vd server chua co public URL de nhan IPN).
     */
    public VnpayQueryResult queryTransactionStatus(
            String vnpTxnRef, LocalDateTime originalTransactionDate, String orderInfo, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String createDate = now.format(DATE_FORMAT);
        String transactionDate = originalTransactionDate.format(DATE_FORMAT);

        String hashData = String.join("|",
                requestId, VNP_VERSION, "querydr", tmnCode, vnpTxnRef, transactionDate, createDate,
                clientIp, orderInfo);
        String secureHash = hmacSHA512(hashSecret, hashData);

        Map<String, Object> body = new TreeMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", VNP_VERSION);
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", tmnCode);
        body.put("vnp_TxnRef", vnpTxnRef);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", clientIp);
        body.put("vnp_SecureHash", secureHash);

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(refundApiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode responseBody = objectMapper.readTree(response.body());
            String responseCode = responseBody.path("vnp_ResponseCode").asText();

            if (!"00".equals(responseCode)) {
                // VNPay khong tim thay / tu choi cau query — chua ket luan duoc gi ve giao dich
                return VnpayQueryResult.unknown(responseCode);
            }

            String transactionStatus = responseBody.path("vnp_TransactionStatus").asText();
            String transactionNo = responseBody.path("vnp_TransactionNo").asText(null);
            return new VnpayQueryResult(true, transactionStatus, transactionNo);
        } catch (Exception e) {
            log.error("VNPay querydr API call failed for txnRef={}", vnpTxnRef, e);
            return VnpayQueryResult.unknown("CONNECTION_ERROR");
        }
    }

    private String buildEncodedQuery(Map<String, String> sortedParams) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String toVnpAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString();
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA512 not available", e);
        }
    }
}
