package com.thanhnguyen.ecommercebackend.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLockRequest {
    @NotNull
    private Boolean locked;
}
