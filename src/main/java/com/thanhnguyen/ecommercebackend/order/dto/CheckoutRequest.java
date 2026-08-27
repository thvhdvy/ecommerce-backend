package com.thanhnguyen.ecommercebackend.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {
    @NotBlank
    private String shippingRecipientName;

    @NotBlank
    private String shippingPhone;

    @NotBlank
    private String shippingAddress;

    private String shippingNote;

    // Tuy chon — validate + giu cho luot dung nam trong checkout, khong phai endpoint rieng
    // (design doc v2 muc 6.3: giu cho coupon dong thoi voi tao order, cung 1 transaction).
    private String couponCode;
}
