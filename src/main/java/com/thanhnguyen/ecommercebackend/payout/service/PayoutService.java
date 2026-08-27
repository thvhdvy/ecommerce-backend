package com.thanhnguyen.ecommercebackend.payout.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.LedgerEntryResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.PayoutResponse;
import com.thanhnguyen.ecommercebackend.payout.dto.SellerBalanceResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface PayoutService {

    /**
     * Order → COMPLETED: ghi 1 dòng EARNED cho mỗi seller có item trong order (gộp theo sellerId),
     * cộng net_amount vào seller_balances.balance. Gọi trực tiếp qua service interface, TRONG CÙNG
     * transaction với transition COMPLETED — không qua ApplicationEventPublisher (design doc v2 mục
     * 9.3, khác với Notification vì tính đúng đắn sổ sách tài chính không được phép "thỉnh thoảng bỏ
     * sót"). Idempotent: nếu order đã có EARNED entry (unique constraint order_id+seller_id) thì bỏ
     * qua toàn bộ order, không ghi trùng.
     */
    void recordEarning(Long orderId);

    /**
     * Return → REFUNDED: ghi 1 dòng ADJUSTED (net_amount âm), trừ vào seller_balances.balance —
     * không chặn ở 0, seller có thể xuống âm (design doc v2 mục 9.4).
     */
    void recordAdjustment(Long returnRequestId, Long sellerId, BigDecimal amount);

    /** Admin: trả toàn bộ balance hiện tại của seller, tạo 1 dòng seller_payouts. Yêu cầu balance > 0. */
    PayoutResponse payOut(Long sellerId);

    /** Balance hiện tại của seller — 0 nếu seller chưa từng có EARNED entry nào (chưa có row). */
    SellerBalanceResponse getBalance(Long sellerId);

    PageResponse<LedgerEntryResponse> listLedger(Long sellerId, Pageable pageable);

    PageResponse<PayoutResponse> listPayoutsForSeller(Long sellerId, Pageable pageable);

    /** Admin: xem toàn bộ payout, lọc theo sellerId nếu truyền vào (null = xem tất cả). */
    PageResponse<PayoutResponse> listAllPayouts(Long sellerId, Pageable pageable);
}
