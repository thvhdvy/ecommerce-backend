package com.thanhnguyen.ecommercebackend.coupon.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponCreateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponRedemptionResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponResponse;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponUpdateRequest;
import com.thanhnguyen.ecommercebackend.coupon.dto.CouponValidationResponse;
import com.thanhnguyen.ecommercebackend.coupon.entity.Coupon;
import com.thanhnguyen.ecommercebackend.coupon.entity.CouponDiscountType;
import com.thanhnguyen.ecommercebackend.coupon.entity.CouponRedemption;
import com.thanhnguyen.ecommercebackend.coupon.entity.CouponRedemptionStatus;
import com.thanhnguyen.ecommercebackend.coupon.entity.CouponStatus;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponAlreadyUsedException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponInvalidException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponMinOrderNotMetException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponNotFoundException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponUsageLimitExceededException;
import com.thanhnguyen.ecommercebackend.coupon.repository.CouponRedemptionRepository;
import com.thanhnguyen.ecommercebackend.coupon.repository.CouponRepository;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    @Override
    @Transactional
    public CouponResponse create(CouponCreateRequest request) {
        if (request.getDiscountType() == CouponDiscountType.PERCENTAGE
                && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CouponInvalidException("Percentage discount value cannot exceed 100");
        }

        Coupon coupon = new Coupon();
        coupon.setCode(request.getCode());
        coupon.setDiscountType(request.getDiscountType());
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
        coupon.setMinOrderAmount(request.getMinOrderAmount());
        coupon.setUsageLimit(request.getUsageLimit());
        coupon.setUsageReserved(0);
        coupon.setUsageCommitted(0);
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setStartsAt(request.getStartsAt());
        coupon.setEndsAt(request.getEndsAt());

        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public CouponResponse update(Long id, CouponUpdateRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new CouponNotFoundException(id));

        if (request.getDiscountValue() != null) {
            coupon.setDiscountValue(request.getDiscountValue());
        }
        if (request.getMaxDiscountAmount() != null) {
            coupon.setMaxDiscountAmount(request.getMaxDiscountAmount());
        }
        if (request.getMinOrderAmount() != null) {
            coupon.setMinOrderAmount(request.getMinOrderAmount());
        }
        if (request.getUsageLimit() != null) {
            coupon.setUsageLimit(request.getUsageLimit());
        }
        if (request.getStartsAt() != null) {
            coupon.setStartsAt(request.getStartsAt());
        }
        if (request.getEndsAt() != null) {
            coupon.setEndsAt(request.getEndsAt());
        }

        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public CouponResponse updateStatus(Long id, CouponStatusUpdateRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new CouponNotFoundException(id));
        coupon.setStatus(request.getStatus());
        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> list(Pageable pageable) {
        return PageResponse.from(couponRepository.findAll(pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponRedemptionResponse> listRedemptions(Long couponId, Pageable pageable) {
        return PageResponse.from(
                couponRedemptionRepository.findAllByCouponIdOrderByCreatedAtDesc(couponId, pageable)
                        .map(this::toRedemptionResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidationResponse validate(String code, BigDecimal cartTotal) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new CouponNotFoundException(code));

        validateEligibility(coupon, cartTotal);
        BigDecimal discountAmount = computeDiscount(coupon, cartTotal);

        return new CouponValidationResponse(
                coupon.getCode(), coupon.getDiscountType(), discountAmount, cartTotal.subtract(discountAmount));
    }

    @Override
    @Transactional
    public BigDecimal reserve(User currentUser, String code, Long orderId, BigDecimal orderSubtotal) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new CouponNotFoundException(code));

        validateEligibility(coupon, orderSubtotal);
        BigDecimal discountAmount = computeDiscount(coupon, orderSubtotal);

        int updated = couponRepository.reserveUsage(coupon.getId());
        if (updated == 0) {
            throw new CouponUsageLimitExceededException(code);
        }

        // Khong can compensation logic thu cong o day neu insert ben duoi that bai (unique
        // constraint per-user) — exception se lan ra ngoai, rollback ca transaction checkout,
        // tu dong hoan tac luon dong reserveUsage() vua chay o tren (xem javadoc CouponService.reserve).
        try {
            couponRedemptionRepository.save(
                    new CouponRedemption(coupon, orderId, currentUser, discountAmount));
        } catch (DataIntegrityViolationException ex) {
            throw new CouponAlreadyUsedException(code);
        }

        return discountAmount;
    }

    @Override
    @Transactional
    public void commit(Long orderId) {
        CouponRedemption redemption = couponRedemptionRepository.findByOrderId(orderId).orElse(null);
        if (redemption == null || redemption.getStatus() != CouponRedemptionStatus.RESERVED) {
            return; // order khong dung coupon, hoac da xu ly (idempotent guard)
        }

        int updated = couponRepository.commitUsage(redemption.getCoupon().getId());
        if (updated == 0) {
            throw new IllegalStateException(
                    "Failed to commit coupon usage for order " + orderId + " — data inconsistency");
        }

        redemption.setStatus(CouponRedemptionStatus.COMMITTED);
        couponRedemptionRepository.save(redemption);
    }

    @Override
    @Transactional
    public void release(Long orderId) {
        CouponRedemption redemption = couponRedemptionRepository.findByOrderId(orderId).orElse(null);
        if (redemption == null || redemption.getStatus() != CouponRedemptionStatus.RESERVED) {
            return; // order khong dung coupon, da COMMITTED (khong hoan lai), hoac da RELEASED
        }

        int updated = couponRepository.releaseUsage(redemption.getCoupon().getId());
        if (updated == 0) {
            throw new IllegalStateException(
                    "Failed to release coupon usage for order " + orderId + " — data inconsistency");
        }

        redemption.setStatus(CouponRedemptionStatus.RELEASED);
        couponRedemptionRepository.save(redemption);
    }

    private void validateEligibility(Coupon coupon, BigDecimal orderSubtotal) {
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new CouponInvalidException("Coupon is not active: " + coupon.getCode());
        }
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new CouponInvalidException("Coupon is not yet valid: " + coupon.getCode());
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            throw new CouponInvalidException("Coupon has expired: " + coupon.getCode());
        }
        if (orderSubtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new CouponMinOrderNotMetException(coupon.getMinOrderAmount());
        }
    }

    // Discount khong bao gio vuot qua orderSubtotal (khong de total am) va khong am — xem design
    // doc muc 6.2 "Discount tinh ra lon hon tong don".
    private BigDecimal computeDiscount(Coupon coupon, BigDecimal orderSubtotal) {
        BigDecimal discount;
        if (coupon.getDiscountType() == CouponDiscountType.PERCENTAGE) {
            discount = orderSubtotal
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discount = coupon.getMaxDiscountAmount();
            }
        } else {
            discount = coupon.getDiscountValue();
        }

        if (discount.compareTo(orderSubtotal) > 0) {
            discount = orderSubtotal;
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        return discount;
    }

    private CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(), coupon.getCode(), coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(), coupon.getMinOrderAmount(), coupon.getUsageLimit(),
                coupon.getUsageReserved(), coupon.getUsageCommitted(), coupon.getStatus(),
                coupon.getStartsAt(), coupon.getEndsAt(), coupon.getCreatedAt(), coupon.getUpdatedAt());
    }

    private CouponRedemptionResponse toRedemptionResponse(CouponRedemption redemption) {
        return new CouponRedemptionResponse(
                redemption.getId(), redemption.getCoupon().getId(), redemption.getOrderId(),
                redemption.getUser().getId(), redemption.getDiscountAmountSnapshot(),
                redemption.getStatus(), redemption.getCreatedAt());
    }
}
