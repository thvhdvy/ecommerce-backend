package com.thanhnguyen.ecommercebackend.payment.dto;

import com.thanhnguyen.ecommercebackend.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {
    private Long orderId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String vnpTransactionNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
