package com.thanhnguyen.ecommercebackend.user.controller;

import java.util.Set;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.common.PageRequests;
import com.thanhnguyen.ecommercebackend.common.ApiResponse;
import com.thanhnguyen.ecommercebackend.user.dto.LockRequest;
import com.thanhnguyen.ecommercebackend.user.dto.UserResponse;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "email");

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> listAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.listAll(PageRequests.capped(pageable, SORTABLE_FIELDS))));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<UserResponse>> setLocked(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id, @Valid @RequestBody LockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.setLocked(currentUser.getId(), id, request.getLocked())));
    }
}
