package com.thanhnguyen.ecommercebackend.payment.util;

/**
 * Ket qua tra ve tu VNPay querydr API.
 * queried=false: khong hoi duoc VNPay (loi ket noi, sai signature...) — chua ket luan duoc gi.
 * queried=true, transactionStatus: "00" = giao dich thanh cong, gia tri khac = that bai/dang xu ly
 * (xem tai lieu VNPay de biet day du danh sach ma).
 */
public record VnpayQueryResult(boolean queried, String transactionStatus, String vnpTransactionNo) {
    public static VnpayQueryResult unknown(String reason) {
        return new VnpayQueryResult(false, reason, null);
    }

    public boolean isSuccess() {
        return queried && "00".equals(transactionStatus);
    }
}
