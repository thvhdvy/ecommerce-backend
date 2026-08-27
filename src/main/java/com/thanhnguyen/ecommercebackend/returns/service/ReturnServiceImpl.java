package com.thanhnguyen.ecommercebackend.returns.service;

import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.inventory.service.InventoryService;
import com.thanhnguyen.ecommercebackend.order.dto.OrderItemReturnInfo;
import com.thanhnguyen.ecommercebackend.order.entity.OrderStatus;
import com.thanhnguyen.ecommercebackend.order.exception.OrderItemNotFoundException;
import com.thanhnguyen.ecommercebackend.order.exception.OrderOwnershipException;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.payment.service.PaymentService;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnCreateRequest;
import com.thanhnguyen.ecommercebackend.returns.dto.ReturnResponse;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequest;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnRequestStatus;
import com.thanhnguyen.ecommercebackend.returns.entity.ReturnStatusHistory;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnAlreadyActiveException;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnNotEligibleException;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnNotFoundException;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnOwnershipException;
import com.thanhnguyen.ecommercebackend.returns.exception.ReturnStatusNotAllowedException;
import com.thanhnguyen.ecommercebackend.returns.repository.ReturnRequestRepository;
import com.thanhnguyen.ecommercebackend.returns.repository.ReturnStatusHistoryRepository;
import com.thanhnguyen.ecommercebackend.user.entity.Seller;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.service.SellerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private static final int RETURN_WINDOW_DAYS = 7;
    private static final int RETURN_SHIP_BACK_DAYS = 7;
    private static final int MAINTENANCE_BATCH_SIZE = 100;

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnStatusHistoryRepository returnStatusHistoryRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final SellerService sellerService;
    private final ReturnRefundResultApplier refundResultApplier;
    private final ReturnMaintenanceProcessor maintenanceProcessor;

    @Override
    @Transactional
    public ReturnResponse create(User currentUser, ReturnCreateRequest request) {
        OrderItemReturnInfo info = orderService.getOrderItemForReturn(request.getOrderItemId());
        if (info == null) {
            throw new OrderItemNotFoundException(request.getOrderItemId());
        }
        if (!info.getCustomerId().equals(currentUser.getId())) {
            throw new OrderOwnershipException();
        }
        validateEligibility(info);

        BigDecimal refundAmount = proratedRefundAmount(info);

        ReturnRequest returnRequest = new ReturnRequest(
                info.getOrderId(), info.getOrderItemId(), currentUser, info.getSellerId(),
                request.getReason(), request.getNote(), refundAmount);

        ReturnRequest saved;
        try {
            saved = returnRequestRepository.save(returnRequest);
        } catch (DataIntegrityViolationException ex) {
            throw new ReturnAlreadyActiveException(request.getOrderItemId());
        }
        returnStatusHistoryRepository.save(
                new ReturnStatusHistory(saved, null, ReturnRequestStatus.REQUESTED, currentUser, null));

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> listMyReturns(User currentUser, Pageable pageable) {
        return PageResponse.from(
                returnRequestRepository.findAllByUser_IdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                        .map(this::toResponse));
    }

    @Override
    @Transactional
    public ReturnResponse cancel(User currentUser, Long returnId) {
        ReturnRequest r = findOrThrow(returnId);
        if (!r.getUser().getId().equals(currentUser.getId())) {
            throw new ReturnOwnershipException();
        }
        if (r.getStatus() != ReturnRequestStatus.REQUESTED) {
            throw new ReturnStatusNotAllowedException(
                    "Can only cancel a return request while REQUESTED, current status: " + r.getStatus());
        }

        transition(r, ReturnRequestStatus.CANCELLED, currentUser, null);
        return toResponse(returnRequestRepository.save(r));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> listSellerReturns(User currentUser, Pageable pageable) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());
        return PageResponse.from(
                returnRequestRepository.findAllBySellerIdOrderByCreatedAtDesc(seller.getId(), pageable)
                        .map(this::toResponse));
    }

    @Override
    @Transactional
    public ReturnResponse approve(User currentUser, Long returnId) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());
        ReturnRequest r = findOrThrow(returnId);
        if (!r.getSellerId().equals(seller.getId())) {
            throw new ReturnOwnershipException();
        }
        return doApprove(r, currentUser);
    }

    @Override
    @Transactional
    public ReturnResponse forceApprove(Long returnId) {
        ReturnRequest r = findOrThrow(returnId);
        return doApprove(r, null);
    }

    private ReturnResponse doApprove(ReturnRequest r, User actor) {
        if (r.getStatus() != ReturnRequestStatus.REQUESTED) {
            throw new ReturnStatusNotAllowedException(
                    "Can only approve a return request while REQUESTED, current status: " + r.getStatus());
        }
        transition(r, ReturnRequestStatus.APPROVED, actor, null);
        r.setApprovedAt(LocalDateTime.now());
        r.setExpiresAt(LocalDateTime.now().plusDays(RETURN_SHIP_BACK_DAYS));
        return toResponse(returnRequestRepository.save(r));
    }

    @Override
    @Transactional
    public ReturnResponse reject(User currentUser, Long returnId) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());
        ReturnRequest r = findOrThrow(returnId);
        if (!r.getSellerId().equals(seller.getId())) {
            throw new ReturnOwnershipException();
        }
        if (r.getStatus() != ReturnRequestStatus.REQUESTED) {
            throw new ReturnStatusNotAllowedException(
                    "Can only reject a return request while REQUESTED, current status: " + r.getStatus());
        }

        transition(r, ReturnRequestStatus.REJECTED, currentUser, null);
        return toResponse(returnRequestRepository.save(r));
    }

    @Override
    @Transactional
    public ReturnResponse markItemReceived(User currentUser, Long returnId) {
        Seller seller = sellerService.requireActiveSeller(currentUser.getId());
        ReturnRequest r = findOrThrow(returnId);
        if (!r.getSellerId().equals(seller.getId())) {
            throw new ReturnOwnershipException();
        }
        if (r.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new ReturnStatusNotAllowedException(
                    "Can only mark item-received while APPROVED, current status: " + r.getStatus());
        }

        // Hang vat ly quay lai kho — chi cong thang available, khong dung reserved (khac releaseStock,
        // xem InventoryService.restock javadoc va design doc v2 muc 7.3).
        OrderItemReturnInfo info = orderService.getOrderItemForReturn(r.getOrderItemId());
        inventoryService.restock(info.getProductId(), info.getQuantity());

        transition(r, ReturnRequestStatus.ITEM_RECEIVED, currentUser, null);
        r.setItemReceivedAt(LocalDateTime.now());
        // Auto-transition sang REFUND_PENDING ngay — day chi la ghi DB thuan (chua goi VNPay), an
        // toan nam trong cung transaction voi restock/ITEM_RECEIVED.
        transition(r, ReturnRequestStatus.REFUND_PENDING, null, null);
        ReturnRequest saved = returnRequestRepository.save(r);

        refundAfterCommit(saved.getId(), saved.getOrderId(), saved.getRefundAmountSnapshot());

        return toResponse(saved);
    }

    // VNPay refund la loi goi mang ra ngoai — KHONG duoc chay trong luc dang giu transaction/lock
    // cua return_requests/inventory vua cap nhat o tren. Chi trigger sau khi transaction hien tai
    // commit xong — dung y het pattern OrderServiceImpl.refundAfterCommit (design doc v2 muc 7.3,
    // tranh tai pham loi da sua o commit daf5c49).
    private void refundAfterCommit(Long returnRequestId, Long orderId, BigDecimal amount) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processRefund(returnRequestId, orderId, amount);
                }
            });
        } else {
            processRefund(returnRequestId, orderId, amount);
        }
    }

    private void processRefund(Long returnRequestId, Long orderId, BigDecimal amount) {
        boolean success;
        try {
            success = paymentService.refundPartial(
                    orderId, amount, "Return #" + returnRequestId, "127.0.0.1", returnRequestId);
        } catch (Exception ex) {
            log.error("Refund failed for return request {}", returnRequestId, ex);
            success = false;
        }
        refundResultApplier.applyResult(returnRequestId, success);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> listAllReturns(Pageable pageable) {
        return PageResponse.from(returnRequestRepository.findAll(pageable).map(this::toResponse));
    }

    @Override
    public ReturnResponse retryRefund(Long returnId) {
        ReturnRequest r = findOrThrow(returnId);
        if (r.getStatus() != ReturnRequestStatus.REFUND_FAILED) {
            throw new ReturnStatusNotAllowedException(
                    "Can only retry refund while REFUND_FAILED, current status: " + r.getStatus());
        }

        refundResultApplier.markPendingForRetry(returnId);
        processRefund(returnId, r.getOrderId(), r.getRefundAmountSnapshot());

        return toResponse(findOrThrow(returnId));
    }

    // Khong @Transactional o day (co chu dich): moi request duoc xu ly trong transaction RIENG boi
    // maintenanceProcessor, cung mo hinh voi OrderServiceImpl.expirePendingPayments.
    @Override
    public void autoExpireApprovedReturns() {
        List<Long> ids = returnRequestRepository.findIdsByStatusAndExpiresAtBefore(
                ReturnRequestStatus.APPROVED, LocalDateTime.now(), PageRequest.of(0, MAINTENANCE_BATCH_SIZE));

        for (Long id : ids) {
            try {
                maintenanceProcessor.expireOne(id, RETURN_SHIP_BACK_DAYS);
            } catch (Exception ex) {
                log.error("Failed to auto-expire return request {}", id, ex);
            }
        }
    }

    private void validateEligibility(OrderItemReturnInfo info) {
        if (info.getOrderStatus() != OrderStatus.DELIVERED && info.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new ReturnNotEligibleException(
                    "Order must be DELIVERED or COMPLETED to request a return, current status: "
                            + info.getOrderStatus());
        }
        if (info.getDeliveredAt() == null
                || info.getDeliveredAt().plusDays(RETURN_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw new ReturnNotEligibleException("Return window has expired");
        }
    }

    // Prorate theo ty le discount cua order (design doc v2 muc 7.3) — tranh tong refund tung item
    // vuot payments.amount khi order co dung coupon.
    private BigDecimal proratedRefundAmount(OrderItemReturnInfo info) {
        BigDecimal grossItemAmount = info.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(info.getQuantity()));
        BigDecimal totalPlusDiscount = info.getOrderTotalAmount().add(info.getOrderDiscountAmount());
        if (totalPlusDiscount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discountRatio = info.getOrderTotalAmount()
                .divide(totalPlusDiscount, 6, RoundingMode.HALF_UP);
        return grossItemAmount.multiply(discountRatio).setScale(2, RoundingMode.HALF_UP);
    }

    private void transition(ReturnRequest r, ReturnRequestStatus newStatus, User actor, String reason) {
        ReturnRequestStatus previous = r.getStatus();
        r.setStatus(newStatus);
        returnStatusHistoryRepository.save(new ReturnStatusHistory(r, previous, newStatus, actor, reason));
    }

    private ReturnRequest findOrThrow(Long returnId) {
        return returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ReturnNotFoundException(returnId));
    }

    private ReturnResponse toResponse(ReturnRequest r) {
        return new ReturnResponse(
                r.getId(), r.getOrderId(), r.getOrderItemId(), r.getUser().getId(), r.getSellerId(),
                r.getReason(), r.getNote(), r.getRefundAmountSnapshot(), r.getStatus(),
                r.getApprovedAt(), r.getItemReceivedAt(), r.getExpiresAt(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
