package com.thanhnguyen.ecommercebackend.notification.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.notification.dto.NotificationResponse;
import com.thanhnguyen.ecommercebackend.notification.entity.NotificationStatus;
import com.thanhnguyen.ecommercebackend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

// Chi de van hanh/debug (khong co API phia customer o v2 — design doc muc 8.4).
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @RequestParam(required = false) NotificationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.listAll(status, PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<NotificationResponse>> retry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.retry(id)));
    }
}
