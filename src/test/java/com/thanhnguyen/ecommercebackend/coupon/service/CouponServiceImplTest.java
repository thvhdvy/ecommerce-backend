package com.thanhnguyen.ecommercebackend.coupon.service;

import com.thanhnguyen.ecommercebackend.coupon.dto.CouponCreateRequest;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;

    @InjectMocks
    private CouponServiceImpl couponService;

    private final User customer = customer();

    private static User customer() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private Coupon percentageCoupon(BigDecimal discountValue, BigDecimal maxDiscountAmount) {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setCode("SALE10");
        coupon.setDiscountType(CouponDiscountType.PERCENTAGE);
        coupon.setDiscountValue(discountValue);
        coupon.setMaxDiscountAmount(maxDiscountAmount);
        coupon.setMinOrderAmount(BigDecimal.ZERO);
        coupon.setStatus(CouponStatus.ACTIVE);
        return coupon;
    }

    private Coupon fixedCoupon(BigDecimal discountValue) {
        Coupon coupon = new Coupon();
        coupon.setId(2L);
        coupon.setCode("FLAT50K");
        coupon.setDiscountType(CouponDiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(discountValue);
        coupon.setMinOrderAmount(BigDecimal.ZERO);
        coupon.setStatus(CouponStatus.ACTIVE);
        return coupon;
    }

    // ---------- validate() — preview, khong reserve ----------

    @Test
    void validate_shouldComputePercentageDiscount_cappedByMax() {
        Coupon coupon = percentageCoupon(new BigDecimal("50"), new BigDecimal("100.00"));
        when(couponRepository.findByCode("SALE10")).thenReturn(Optional.of(coupon));

        CouponValidationResponse response = couponService.validate("SALE10", new BigDecimal("1000.00"));

        // 50% cua 1000 = 500, nhung bi chan tran boi maxDiscountAmount = 100
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getFinalTotal()).isEqualByComparingTo("900.00");
        verify(couponRepository, never()).reserveUsage(anyLong());
        verify(couponRedemptionRepository, never()).save(any());
    }

    @Test
    void validate_shouldClampDiscount_whenFixedAmountExceedsSubtotal() {
        Coupon coupon = fixedCoupon(new BigDecimal("100.00"));
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));

        CouponValidationResponse response = couponService.validate("FLAT50K", new BigDecimal("80.00"));

        assertThat(response.getDiscountAmount()).isEqualByComparingTo("80.00");
        assertThat(response.getFinalTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void validate_shouldThrow_whenCouponNotFound() {
        when(couponRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.validate("NOPE", BigDecimal.TEN))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void validate_shouldThrow_whenCouponInactive() {
        Coupon coupon = fixedCoupon(new BigDecimal("10.00"));
        coupon.setStatus(CouponStatus.INACTIVE);
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("FLAT50K", new BigDecimal("100.00")))
                .isInstanceOf(CouponInvalidException.class);
    }

    @Test
    void validate_shouldThrow_whenNotYetStarted() {
        Coupon coupon = fixedCoupon(new BigDecimal("10.00"));
        coupon.setStartsAt(LocalDateTime.now().plusDays(1));
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("FLAT50K", new BigDecimal("100.00")))
                .isInstanceOf(CouponInvalidException.class);
    }

    @Test
    void validate_shouldThrow_whenExpired() {
        Coupon coupon = fixedCoupon(new BigDecimal("10.00"));
        coupon.setEndsAt(LocalDateTime.now().minusDays(1));
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("FLAT50K", new BigDecimal("100.00")))
                .isInstanceOf(CouponInvalidException.class);
    }

    @Test
    void validate_shouldThrow_whenBelowMinOrderAmount() {
        Coupon coupon = fixedCoupon(new BigDecimal("10.00"));
        coupon.setMinOrderAmount(new BigDecimal("200.00"));
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.validate("FLAT50K", new BigDecimal("100.00")))
                .isInstanceOf(CouponMinOrderNotMetException.class);
    }

    // ---------- reserve() ----------

    @Test
    void reserve_shouldReturnDiscount_andSaveRedemption_whenEligible() {
        Coupon coupon = fixedCoupon(new BigDecimal("50.00"));
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));
        when(couponRepository.reserveUsage(2L)).thenReturn(1);
        when(couponRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal discount = couponService.reserve(customer, "FLAT50K", 100L, new BigDecimal("200.00"));

        assertThat(discount).isEqualByComparingTo("50.00");
        verify(couponRedemptionRepository).save(any(CouponRedemption.class));
    }

    @Test
    void reserve_shouldThrow_whenUsageLimitExceeded() {
        Coupon coupon = fixedCoupon(new BigDecimal("50.00"));
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));
        when(couponRepository.reserveUsage(2L)).thenReturn(0); // conditional UPDATE khong match dong nao

        assertThatThrownBy(() -> couponService.reserve(customer, "FLAT50K", 100L, new BigDecimal("200.00")))
                .isInstanceOf(CouponUsageLimitExceededException.class);
        verify(couponRedemptionRepository, never()).save(any());
    }

    @Test
    void reserve_shouldThrow_whenUserAlreadyUsedCoupon() {
        Coupon coupon = fixedCoupon(new BigDecimal("50.00"));
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));
        when(couponRepository.reserveUsage(2L)).thenReturn(1);
        when(couponRedemptionRepository.save(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        // Vi pham unique index (coupon_id, user_id) WHERE status IN (RESERVED, COMMITTED) — mo phong
        // race 2 request dong thoi cung 1 user (design doc muc 6.4).
        assertThatThrownBy(() -> couponService.reserve(customer, "FLAT50K", 100L, new BigDecimal("200.00")))
                .isInstanceOf(CouponAlreadyUsedException.class);
    }

    @Test
    void reserve_shouldNotReserveUsage_whenEligibilityFailsBeforeReserveUsage() {
        Coupon coupon = fixedCoupon(new BigDecimal("50.00"));
        coupon.setStatus(CouponStatus.INACTIVE);
        when(couponRepository.findByCode("FLAT50K")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.reserve(customer, "FLAT50K", 100L, new BigDecimal("200.00")))
                .isInstanceOf(CouponInvalidException.class);

        verify(couponRepository, never()).reserveUsage(anyLong());
    }

    // ---------- commit() ----------

    @Test
    void commit_shouldNoOp_whenOrderHasNoCoupon() {
        when(couponRedemptionRepository.findByOrderId(100L)).thenReturn(Optional.empty());

        couponService.commit(100L);

        verify(couponRepository, never()).commitUsage(anyLong());
    }

    @Test
    void commit_shouldNoOp_whenRedemptionNotReserved() {
        CouponRedemption redemption = new CouponRedemption(fixedCoupon(BigDecimal.TEN), 100L, customer, BigDecimal.TEN);
        redemption.setStatus(CouponRedemptionStatus.RELEASED);
        when(couponRedemptionRepository.findByOrderId(100L)).thenReturn(Optional.of(redemption));

        couponService.commit(100L);

        verify(couponRepository, never()).commitUsage(anyLong());
    }

    @Test
    void commit_shouldTransitionToCommitted_whenReserved() {
        Coupon coupon = fixedCoupon(BigDecimal.TEN);
        CouponRedemption redemption = new CouponRedemption(coupon, 100L, customer, BigDecimal.TEN);
        when(couponRedemptionRepository.findByOrderId(100L)).thenReturn(Optional.of(redemption));
        when(couponRepository.commitUsage(2L)).thenReturn(1);
        when(couponRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        couponService.commit(100L);

        assertThat(redemption.getStatus()).isEqualTo(CouponRedemptionStatus.COMMITTED);
    }

    @Test
    void commit_shouldThrow_whenCommitUsageAffectsNoRows() {
        Coupon coupon = fixedCoupon(BigDecimal.TEN);
        CouponRedemption redemption = new CouponRedemption(coupon, 100L, customer, BigDecimal.TEN);
        when(couponRedemptionRepository.findByOrderId(100L)).thenReturn(Optional.of(redemption));
        when(couponRepository.commitUsage(2L)).thenReturn(0);

        assertThatThrownBy(() -> couponService.commit(100L)).isInstanceOf(IllegalStateException.class);
    }

    // ---------- release() ----------

    @Test
    void release_shouldNoOp_whenAlreadyCommitted() {
        Coupon coupon = fixedCoupon(BigDecimal.TEN);
        CouponRedemption redemption = new CouponRedemption(coupon, 100L, customer, BigDecimal.TEN);
        redemption.setStatus(CouponRedemptionStatus.COMMITTED);
        when(couponRedemptionRepository.findByOrderId(100L)).thenReturn(Optional.of(redemption));

        couponService.release(100L);

        // Da COMMITTED (thanh toan xong) -> khong hoan lai luot dung, dung nguyen tac cancel-sau-payment.
        verify(couponRepository, never()).releaseUsage(anyLong());
    }

    @Test
    void release_shouldTransitionToReleased_whenReserved() {
        Coupon coupon = fixedCoupon(BigDecimal.TEN);
        CouponRedemption redemption = new CouponRedemption(coupon, 100L, customer, BigDecimal.TEN);
        when(couponRedemptionRepository.findByOrderId(100L)).thenReturn(Optional.of(redemption));
        when(couponRepository.releaseUsage(2L)).thenReturn(1);
        when(couponRedemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        couponService.release(100L);

        assertThat(redemption.getStatus()).isEqualTo(CouponRedemptionStatus.RELEASED);
    }

    // ---------- create() ----------

    @Test
    void create_shouldThrow_whenPercentageValueOver100() {
        CouponCreateRequest request = new CouponCreateRequest(
                "BAD", CouponDiscountType.PERCENTAGE, new BigDecimal("150"), null, BigDecimal.ZERO, null, null, null);

        assertThatThrownBy(() -> couponService.create(request)).isInstanceOf(CouponInvalidException.class);
        verify(couponRepository, never()).save(any());
    }
}
