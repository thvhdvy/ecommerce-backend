package com.thanhnguyen.ecommercebackend.coupon.dto;

import com.thanhnguyen.ecommercebackend.coupon.entity.CouponStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponStatusUpdateRequest {
    @NotNull
    private CouponStatus status;
}
