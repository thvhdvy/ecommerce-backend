package com.thanhnguyen.ecommercebackend.report.service;

import com.thanhnguyen.ecommercebackend.report.dto.RevenueByCategoryResponse;
import com.thanhnguyen.ecommercebackend.report.dto.RevenueByDayResponse;
import com.thanhnguyen.ecommercebackend.report.dto.TopProductResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    List<RevenueByDayResponse> revenueByDay(LocalDate from, LocalDate to);

    List<RevenueByCategoryResponse> revenueByCategory(LocalDate from, LocalDate to);

    List<TopProductResponse> topProducts(LocalDate from, LocalDate to, int limit);
}
