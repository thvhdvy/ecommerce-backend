package com.thanhnguyen.ecommercebackend.payment.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayClientTest {

    private final VnpayClient vnpayClient = new VnpayClient(
            "TESTTMN",
            "TESTSECRET",
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
            "http://localhost:8080/api/payments/vnpay-return");

    @Test
    void buildPaymentUrl_shouldContainAllRequiredParamsAndValidSecureHash() {
        String url = vnpayClient.buildPaymentUrl("1-123456", new BigDecimal("45.00"), 1L, "127.0.0.1");

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(url).contains("vnp_TmnCode=TESTTMN");
        assertThat(url).contains("vnp_Amount=4500"); // 45.00 VND * 100, khong thap phan
        assertThat(url).contains("vnp_TxnRef=1-123456");
        assertThat(url).contains("vnp_SecureHash=");

        Map<String, String> params = parseQuery(url);
        assertThat(vnpayClient.verifySignature(params)).isTrue();
    }

    @Test
    void verifySignature_shouldReturnFalse_whenAmountTampered() {
        String url = vnpayClient.buildPaymentUrl("1-123456", new BigDecimal("45.00"), 1L, "127.0.0.1");
        Map<String, String> params = parseQuery(url);

        params.put("vnp_Amount", "999999");

        assertThat(vnpayClient.verifySignature(params)).isFalse();
    }

    @Test
    void verifySignature_shouldReturnFalse_whenHashMissing() {
        assertThat(vnpayClient.verifySignature(new HashMap<>())).isFalse();
    }

    private Map<String, String> parseQuery(String url) {
        String query = url.substring(url.indexOf('?') + 1);
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.US_ASCII));
        }
        return params;
    }
}
