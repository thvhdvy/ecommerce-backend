package com.thanhnguyen.ecommercebackend.shipping.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignShipperRequest {
    @NotNull
    private Long shipperId;
}
