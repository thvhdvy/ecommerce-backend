package com.thanhnguyen.ecommercebackend.common;

import com.thanhnguyen.ecommercebackend.cart.exception.CartItemNotFoundException;
import com.thanhnguyen.ecommercebackend.cart.exception.CartOwnershipException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponAlreadyUsedException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponInvalidException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponMinOrderNotMetException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponNotFoundException;
import com.thanhnguyen.ecommercebackend.coupon.exception.CouponUsageLimitExceededException;
import com.thanhnguyen.ecommercebackend.inventory.exception.InsufficientStockException;
import com.thanhnguyen.ecommercebackend.inventory.exception.InventoryNotFoundException;
import com.thanhnguyen.ecommercebackend.order.exception.EmptyCartException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderCancelNotAllowedException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderItemNotFoundException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderItemStatusNotAllowedException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderNotFoundException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderOwnershipException;
import com.thanhnguyen.ecommercebackend.payment.exception.PaymentNotAllowedException;
import com.thanhnguyen.ecommercebackend.payment.exception.PaymentNotFoundException;
import com.thanhnguyen.ecommercebackend.payment.exception.RefundNotAllowedException;
import com.thanhnguyen.ecommercebackend.payment.exception.RefundNotFoundException;
import com.thanhnguyen.ecommercebackend.product.exception.BrandNotFoundException;
import com.thanhnguyen.ecommercebackend.product.exception.CategoryNotFoundException;
import com.thanhnguyen.ecommercebackend.common.InvalidSortFieldException;
import com.thanhnguyen.ecommercebackend.product.exception.MultiplePrimaryImagesException;
import com.thanhnguyen.ecommercebackend.user.exception.NotASellerException;
import com.thanhnguyen.ecommercebackend.product.exception.ProductNotFoundException;
import com.thanhnguyen.ecommercebackend.product.exception.ProductOwnershipException;
import com.thanhnguyen.ecommercebackend.review.exception.ReviewAlreadyExistsException;
import com.thanhnguyen.ecommercebackend.review.exception.ReviewNotEligibleException;
import com.thanhnguyen.ecommercebackend.review.exception.ReviewNotFoundException;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryNotAllowedException;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryNotFoundException;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryOwnershipException;
import com.thanhnguyen.ecommercebackend.shipping.exception.NotAShipperException;
import com.thanhnguyen.ecommercebackend.user.exception.AccountLockedException;
import com.thanhnguyen.ecommercebackend.user.exception.EmailAlreadyExistsException;
import com.thanhnguyen.ecommercebackend.user.exception.InvalidCredentialsException;
import com.thanhnguyen.ecommercebackend.user.exception.InvalidRefreshTokenException;
import com.thanhnguyen.ecommercebackend.user.exception.InvalidResetTokenException;
import com.thanhnguyen.ecommercebackend.user.exception.NotEligibleForSellerException;
import com.thanhnguyen.ecommercebackend.user.exception.SelfLockNotAllowedException;
import com.thanhnguyen.ecommercebackend.user.exception.SellerAlreadyExistsException;
import com.thanhnguyen.ecommercebackend.user.exception.SellerLockedException;
import com.thanhnguyen.ecommercebackend.user.exception.TooManyAttemptsException;
import com.thanhnguyen.ecommercebackend.user.exception.SellerNotFoundException;
import com.thanhnguyen.ecommercebackend.user.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("EMAIL_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("INVALID_CREDENTIALS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("INVALID_REFRESH_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCOUNT_LOCKED", ex.getMessage()));
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyAttempts(TooManyAttemptsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("TOO_MANY_ATTEMPTS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidResetToken(InvalidResetTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("INVALID_RESET_TOKEN", ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("USER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(SellerAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleSellerAlreadyExists(SellerAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("SELLER_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(NotEligibleForSellerException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotEligibleForSeller(NotEligibleForSellerException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("NOT_ELIGIBLE_FOR_SELLER", ex.getMessage()));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCategoryNotFound(CategoryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("CATEGORY_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBrandNotFound(BrandNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("BRAND_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("PRODUCT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("ORDER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(OrderOwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderOwnership(OrderOwnershipException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ORDER_OWNERSHIP_VIOLATION", ex.getMessage()));
    }

    @ExceptionHandler(OrderItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderItemNotFound(OrderItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("ORDER_ITEM_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(OrderItemStatusNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderItemStatusNotAllowed(OrderItemStatusNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("ORDER_ITEM_STATUS_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmptyCart(EmptyCartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("EMPTY_CART", ex.getMessage()));
    }

    @ExceptionHandler(OrderCancelNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderCancelNotAllowed(OrderCancelNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("ORDER_CANCEL_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientStock(InsufficientStockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("INSUFFICIENT_STOCK", ex.getMessage()));
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInventoryNotFound(InventoryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("INVENTORY_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartItemNotFound(CartItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("CART_ITEM_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CartOwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartOwnership(CartOwnershipException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("CART_OWNERSHIP_VIOLATION", ex.getMessage()));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("PAYMENT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(PaymentNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentNotAllowed(PaymentNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("PAYMENT_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(RefundNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleRefundNotAllowed(RefundNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("REFUND_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(RefundNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRefundNotFound(RefundNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("REFUND_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MultiplePrimaryImagesException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultiplePrimaryImages(MultiplePrimaryImagesException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("MULTIPLE_PRIMARY_IMAGES", ex.getMessage()));
    }

    @ExceptionHandler(InvalidSortFieldException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSortField(InvalidSortFieldException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_SORT_FIELD", ex.getMessage()));
    }

    @ExceptionHandler(NotASellerException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotASeller(NotASellerException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("NOT_A_SELLER", ex.getMessage()));
    }

    @ExceptionHandler(ProductOwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductOwnership(ProductOwnershipException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("PRODUCT_OWNERSHIP_VIOLATION", ex.getMessage()));
    }

    @ExceptionHandler(DeliveryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryNotFound(DeliveryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("DELIVERY_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(DeliveryOwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryOwnership(DeliveryOwnershipException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("DELIVERY_OWNERSHIP_VIOLATION", ex.getMessage()));
    }

    @ExceptionHandler(DeliveryNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeliveryNotAllowed(DeliveryNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DELIVERY_NOT_ALLOWED", ex.getMessage()));
    }

    // 403 (khong phai 409 nhu truoc): cung ban chat "khong du quyen/khong dung vai tro" voi
    // NotASellerException — thong nhat status code cho 2 case tuong duong.
    @ExceptionHandler(NotAShipperException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAShipper(NotAShipperException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("NOT_A_SHIPPER", ex.getMessage()));
    }

    @ExceptionHandler(SelfLockNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSelfLockNotAllowed(SelfLockNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("SELF_LOCK_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(SellerLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSellerLocked(SellerLockedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("SELLER_LOCKED", ex.getMessage()));
    }

    @ExceptionHandler(SellerNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSellerNotFound(SellerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("SELLER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewNotFound(ReviewNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("REVIEW_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ReviewAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewAlreadyExists(ReviewAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("REVIEW_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(ReviewNotEligibleException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewNotEligible(ReviewNotEligibleException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("REVIEW_NOT_ELIGIBLE", ex.getMessage()));
    }

    @ExceptionHandler(CouponNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponNotFound(CouponNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("COUPON_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CouponInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponInvalid(CouponInvalidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("COUPON_INVALID", ex.getMessage()));
    }

    @ExceptionHandler(CouponMinOrderNotMetException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponMinOrderNotMet(CouponMinOrderNotMetException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("COUPON_MIN_ORDER_NOT_MET", ex.getMessage()));
    }

    @ExceptionHandler(CouponUsageLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponUsageLimitExceeded(CouponUsageLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("COUPON_USAGE_LIMIT_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(CouponAlreadyUsedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponAlreadyUsed(CouponAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("COUPON_ALREADY_USED", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_PARAMETER", message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("METHOD_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "No handler found for this request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("MALFORMED_REQUEST_BODY", "Request body is missing or malformed"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Unexpected error occurred"));
    }
}
