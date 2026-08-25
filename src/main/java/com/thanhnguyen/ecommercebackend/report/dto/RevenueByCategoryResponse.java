package com.thanhnguyen.ecommercebackend.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueByCategoryResponse {
    private Long categoryId;
    private String categoryName;
    private BigDecimal grossRevenue;
}
