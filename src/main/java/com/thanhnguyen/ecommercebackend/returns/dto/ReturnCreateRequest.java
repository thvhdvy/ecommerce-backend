package com.thanhnguyen.ecommercebackend.returns.dto;

import com.thanhnguyen.ecommercebackend.returns.entity.ReturnReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnCreateRequest {
    @NotNull
    private Long orderItemId;

    @NotNull
    private ReturnReason reason;

    @Size(max = 1000)
    private String note;
}
