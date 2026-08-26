package com.thanhnguyen.ecommercebackend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResolveRefundRequest {
    @NotBlank
    @Size(max = 500)
    private String note;
}
