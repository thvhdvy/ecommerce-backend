package com.thanhnguyen.ecommercebackend.report.repository;

import java.math.BigDecimal;

public interface TopProductProjection {
    Long getProductId();
    String getProductName();
    Long getQuantitySold();
    BigDecimal getGrossRevenue();
}
