package com.thanhnguyen.ecommercebackend.returns.controller;

import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnCreateRequest;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnResponse;
import com.thanhnguyen.ecommercebackend.returns.service.ReturnService;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    private final ReturnService returnService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReturnResponse>> create(
            @AuthenticationPrincipal User currentUser, @Valid @RequestBody ReturnCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(returnService.create(currentUser, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> listMine(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                returnService.listMyReturns(currentUser, PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReturnResponse>> cancel(
            @AuthenticationPrincipal User currentUser, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.cancel(currentUser, id)));
    }
}
