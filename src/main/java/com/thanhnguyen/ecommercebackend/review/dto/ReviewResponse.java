package com.thanhnguyen.ecommercebackend.review.dto;

import com.thanhnguyen.ecommercebackend.review.entity.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long productId;
    private Long userId;
    private String userFullName;
    private Integer rating;
    private String comment;
    private ReviewStatus status;
    private LocalDateTime createdAt;
}
