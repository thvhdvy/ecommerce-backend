package com.thanhnguyen.ecommercebackend.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Partial update — null nghia la khong doi (cung pattern voi ProductUpdateRequest). Khong cho sua
// code/discountType sau khi tao de tranh doi ban chat coupon da co the dang duoc dung.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponUpdateRequest {
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal discountValue;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0.0")
    private BigDecimal minOrderAmount;

    @Min(1)
    private Integer usageLimit;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;
}
