package com.thanhnguyen.ecommercebackend.returns.dto;

import com.thanhnguyen.ecommercebackend.returns.entity.ReturnReason;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnResponse {
    private Long id;
    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private Long sellerId;
    private ReturnReason reason;
    private String note;
    private BigDecimal refundAmountSnapshot;
    private ReturnRequestStatus status;
    private LocalDateTime approvedAt;
    private LocalDateTime itemReceivedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
