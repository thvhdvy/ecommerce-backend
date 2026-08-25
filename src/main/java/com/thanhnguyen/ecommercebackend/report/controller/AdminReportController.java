package com.thanhnguyen.ecommercebackend.report.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.report.dto.RevenueByCategoryResponse;
import com.thanhnguyen.ecommercebackend.report.dto.RevenueByDayResponse;
import com.thanhnguyen.ecommercebackend.report.dto.TopProductResponse;
import com.thanhnguyen.ecommercebackend.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private static final int DEFAULT_TOP_PRODUCTS_LIMIT = 10;
    private static final int MAX_TOP_PRODUCTS_LIMIT = 50;

    private final ReportService reportService;

    @GetMapping("/revenue-by-day")
    public ResponseEntity<ApiResponse<List<RevenueByDayResponse>>> revenueByDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reportService.revenueByDay(from, to)));
    }

    @GetMapping("/revenue-by-category")
    public ResponseEntity<ApiResponse<List<RevenueByCategoryResponse>>> revenueByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reportService.revenueByCategory(from, to)));
    }

    @GetMapping("/top-products")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "" + DEFAULT_TOP_PRODUCTS_LIMIT) int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, MAX_TOP_PRODUCTS_LIMIT));
        return ResponseEntity.ok(ApiResponse.success(reportService.topProducts(from, to, cappedLimit)));
    }
}
