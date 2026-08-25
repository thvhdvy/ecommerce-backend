package com.thanhnguyen.ecommercebackend.shipping.dto;

import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryFailureReason;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatusUpdateRequest {
    @NotNull
    private DeliveryStatus status;

    /** Bắt buộc khi status = FAILED (xem business rule Flow 4). */
    private DeliveryFailureReason failureReason;
}
