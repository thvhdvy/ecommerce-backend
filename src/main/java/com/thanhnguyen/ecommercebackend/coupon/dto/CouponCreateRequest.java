package com.thanhnguyen.ecommercebackend.coupon.dto;

import com.thanhnguyen.ecommercebackend.coupon.entity.CouponDiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponCreateRequest {
    @NotBlank
    private String code;

    @NotNull
    private CouponDiscountType discountType;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal discountValue;

    // Chi co y nghia voi PERCENTAGE — validate cross-field nam o service layer (CouponServiceImpl).
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal maxDiscountAmount;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal minOrderAmount;

    @Min(1)
    private Integer usageLimit;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;
}
