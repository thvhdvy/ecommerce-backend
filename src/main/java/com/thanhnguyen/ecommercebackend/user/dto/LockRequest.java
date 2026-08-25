package com.thanhnguyen.ecommercebackend.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Dung chung cho PATCH .../users/{id}/lock va PATCH .../sellers/{id}/lock. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LockRequest {
    @NotNull
    private Boolean locked;
}
