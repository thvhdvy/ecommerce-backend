package com.thanhnguyen.ecommercebackend.payment.util;

public record VnpayRefundResult(boolean success, String vnpTransactionNo, String responseCode) {
    public static VnpayRefundResult success(String vnpTransactionNo) {
        return new VnpayRefundResult(true, vnpTransactionNo, "00");
    }

    public static VnpayRefundResult failure(String responseCode) {
        return new VnpayRefundResult(false, null, responseCode);
    }
}
