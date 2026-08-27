package com.thanhnguyen.ecommercebackend.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidateRequest {
    @NotBlank
    private String code;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal cartTotal;
}
