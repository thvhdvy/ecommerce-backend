package com.thanhnguyen.ecommercebackend.report.repository;

import java.math.BigDecimal;

public interface RevenueByCategoryProjection {
    Long getCategoryId();
    String getCategoryName();
    BigDecimal getGrossRevenue();
}
