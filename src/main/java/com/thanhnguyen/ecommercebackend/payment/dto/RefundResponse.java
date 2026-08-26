package com.thanhnguyen.ecommercebackend.payment.dto;

import com.thanhnguyen.ecommercebackend.payment.entity.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private RefundStatus status;
    private String reason;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
