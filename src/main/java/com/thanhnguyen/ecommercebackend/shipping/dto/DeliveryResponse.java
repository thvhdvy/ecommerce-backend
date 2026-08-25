package com.thanhnguyen.ecommercebackend.shipping.dto;

import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryFailureReason;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResponse {
    private Long id;
    private Long orderId;
    private Long shipperId;
    private String shipperName;
    private DeliveryStatus status;
    private DeliveryFailureReason failureReason;
    private int retryCount;
    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
