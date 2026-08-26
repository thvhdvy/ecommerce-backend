package com.thanhnguyen.ecommercebackend.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDisputeResponse {
    private Long id;
    private String vnpTransactionNo;
    private String eventType;
    private String payload;
    private LocalDateTime processedAt;
}
