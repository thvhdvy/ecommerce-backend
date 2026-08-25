package com.thanhnguyen.ecommercebackend.report.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface RevenueByDayProjection {
    LocalDate getDay();
    BigDecimal getGross();
    BigDecimal getRefund();
}
