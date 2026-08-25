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
}
