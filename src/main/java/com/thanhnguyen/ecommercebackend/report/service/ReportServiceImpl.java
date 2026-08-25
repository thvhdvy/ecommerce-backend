package com.thanhnguyen.ecommercebackend.report.service;

import com.thanhnguyen.ecommercebackend.report.dto.RevenueByCategoryResponse;
import com.thanhnguyen.ecommercebackend.report.dto.RevenueByDayResponse;
import com.thanhnguyen.ecommercebackend.report.dto.TopProductResponse;
import com.thanhnguyen.ecommercebackend.report.repository.ReportQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportQueryRepository reportQueryRepository;

    @Override
    public List<RevenueByDayResponse> revenueByDay(LocalDate from, LocalDate to) {
        return reportQueryRepository.revenueByDay(from, to).stream()
                .map(p -> new RevenueByDayResponse(
                        p.getDay(), p.getGross(), p.getRefund(), p.getGross().subtract(p.getRefund())))
                .toList();
    }

    @Override
    public List<RevenueByCategoryResponse> revenueByCategory(LocalDate from, LocalDate to) {
        return reportQueryRepository.revenueByCategory(from, to).stream()
                .map(p -> new RevenueByCategoryResponse(p.getCategoryId(), p.getCategoryName(), p.getGrossRevenue()))
                .toList();
    }

    @Override
    public List<TopProductResponse> topProducts(LocalDate from, LocalDate to, int limit) {
        return reportQueryRepository.topProducts(from, to, limit).stream()
                .map(p -> new TopProductResponse(p.getProductId(), p.getProductName(), p.getQuantitySold(), p.getGrossRevenue()))
                .toList();
    }
}
