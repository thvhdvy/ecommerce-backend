package com.thanhnguyen.ecommercebackend.shipping.service;

import org.springframework.data.domain.Pageable;
import com.thanhnguyen.ecommercebackend.common.PageResponse;
import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryResponse;
import com.thanhnguyen.ecommercebackend.shipping.dto.DeliveryStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.shipping.entity.Delivery;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatus;
import com.thanhnguyen.ecommercebackend.shipping.entity.DeliveryStatusHistory;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryNotAllowedException;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryNotFoundException;
import com.thanhnguyen.ecommercebackend.shipping.exception.DeliveryOwnershipException;
import com.thanhnguyen.ecommercebackend.shipping.exception.NotAShipperException;
import com.thanhnguyen.ecommercebackend.shipping.repository.DeliveryRepository;
import com.thanhnguyen.ecommercebackend.shipping.repository.DeliveryStatusHistoryRepository;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserRole;
import com.thanhnguyen.ecommercebackend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    private static final Set<DeliveryStatus> IN_PROGRESS_STATUSES = Set.of(DeliveryStatus.ASSIGNED, DeliveryStatus.IN_TRANSIT);
    private static final int MAX_RETRY = 1;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;
    private final UserService userService;
    private final OrderService orderService;

    @Override
    @Transactional
    public DeliveryResponse assignShipper(Long orderId, Long sellerId, Long shipperId, User actor) {
        // Gate theo tung seller doc lap (design doc v2 muc 10.4) — khong con dua vao order.status
        // aggregate nhu v1, vi seller khac co the chua pack xong ma khong duoc chan seller nay.
        if (!orderService.areSellerItemsPacked(orderId, sellerId)) {
            throw new DeliveryNotAllowedException("Seller's order items must be fully PACKED before assigning a shipper");
        }

        // Lookup qua UserService (khong query UserRepository truc tiep — module boundary);
        // rule "phai la SHIPPER" la nghiep vu shipping nen check tai day.
        User shipper = userService.getEntityById(shipperId);
        if (shipper.getRole() != UserRole.SHIPPER) {
            throw new NotAShipperException();
        }

        Delivery delivery = deliveryRepository.findByOrderIdAndSellerId(orderId, sellerId).orElse(null);
        if (delivery == null) {
            delivery = new Delivery(orderId, sellerId, shipper);
            deliveryRepository.save(delivery);
            deliveryStatusHistoryRepository.save(
                    new DeliveryStatusHistory(delivery, null, DeliveryStatus.ASSIGNED, actor, "Shipper assigned"));
        } else {
            DeliveryStatus previousStatus = delivery.getStatus();
            delivery.setShipper(shipper);
            delivery.setStatus(DeliveryStatus.ASSIGNED);
            deliveryRepository.save(delivery);
            deliveryStatusHistoryRepository.save(
                    new DeliveryStatusHistory(delivery, previousStatus, DeliveryStatus.ASSIGNED, actor, "Shipper reassigned"));
        }

        orderService.recomputeAggregateStatus(orderId);
        return toResponse(delivery);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeliveryResponse> listMyDeliveries(User currentUser, Pageable pageable) {
        return PageResponse.from(
                deliveryRepository.findAllByShipperId(currentUser.getId(), pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    public DeliveryResponse updateDeliveryStatus(User currentUser, Long deliveryId, DeliveryStatusUpdateRequest request) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));

        if (delivery.getShipper() == null || !delivery.getShipper().getId().equals(currentUser.getId())) {
            throw new DeliveryOwnershipException();
        }

        if (!IN_PROGRESS_STATUSES.contains(delivery.getStatus())) {
            throw new DeliveryNotAllowedException("Delivery is not in progress, current status: " + delivery.getStatus());
        }

        DeliveryStatus target = request.getStatus();
        DeliveryStatus previousStatus = delivery.getStatus();

        switch (target) {
            case IN_TRANSIT -> applyInTransit(delivery, previousStatus);
            case DELIVERED -> applyDelivered(delivery, previousStatus, delivery.getShipper(), null);
            case FAILED -> applyFailed(delivery, previousStatus, request);
            case ASSIGNED -> throw new DeliveryNotAllowedException("Cannot manually move a delivery back to ASSIGNED");
        }

        return toResponse(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse confirmDeliveredManually(User admin, Long orderId, Long sellerId) {
        Delivery delivery = deliveryRepository.findByOrderIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new DeliveryNotFoundException(orderId));

        if (!IN_PROGRESS_STATUSES.contains(delivery.getStatus())) {
            throw new DeliveryNotAllowedException(
                    "Delivery is not in progress, current status: " + delivery.getStatus());
        }

        applyDelivered(delivery, delivery.getStatus(), admin,
                "Admin xac nhan giao thanh cong thu cong (doi soat ngoai he thong)");
        return toResponse(delivery);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, DeliveryStatus> getDeliveryStatusesBySeller(Long orderId) {
        return deliveryRepository.findAllByOrderId(orderId).stream()
                .collect(Collectors.toMap(Delivery::getSellerId, Delivery::getStatus));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDeliveredForSeller(Long orderId, Long sellerId) {
        return deliveryRepository.findByOrderIdAndSellerId(orderId, sellerId)
                .map(d -> d.getStatus() == DeliveryStatus.DELIVERED)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDateTime getDeliveredAtForSeller(Long orderId, Long sellerId) {
        return deliveryRepository.findByOrderIdAndSellerId(orderId, sellerId)
                .map(Delivery::getDeliveredAt)
                .orElse(null);
    }

    private void applyInTransit(Delivery delivery, DeliveryStatus previousStatus) {
        delivery.setStatus(DeliveryStatus.IN_TRANSIT);
        delivery.setPickedUpAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        deliveryStatusHistoryRepository.save(
                new DeliveryStatusHistory(delivery, previousStatus, DeliveryStatus.IN_TRANSIT, delivery.getShipper(), null));
    }

    private void applyDelivered(Delivery delivery, DeliveryStatus previousStatus, User actor, String note) {
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        deliveryStatusHistoryRepository.save(
                new DeliveryStatusHistory(delivery, previousStatus, DeliveryStatus.DELIVERED, actor, note));
        orderService.recomputeAggregateStatus(delivery.getOrderId());
    }

    private void applyFailed(Delivery delivery, DeliveryStatus previousStatus, DeliveryStatusUpdateRequest request) {
        if (request.getFailureReason() == null) {
            throw new DeliveryNotAllowedException("failureReason is required when status = FAILED");
        }

        delivery.setFailureReason(request.getFailureReason());
        deliveryStatusHistoryRepository.save(
                new DeliveryStatusHistory(delivery, previousStatus, DeliveryStatus.FAILED, delivery.getShipper(),
                        "Failure reason: " + request.getFailureReason()));

        if (delivery.getRetryCount() < MAX_RETRY) {
            delivery.setRetryCount(delivery.getRetryCount() + 1);
            delivery.setStatus(DeliveryStatus.ASSIGNED);
            deliveryRepository.save(delivery);
            // He qua tu dong cua business rule (retry), khong phai hanh dong truc tiep cua shipper -> changed_by = null.
            deliveryStatusHistoryRepository.save(new DeliveryStatusHistory(
                    delivery, DeliveryStatus.FAILED, DeliveryStatus.ASSIGNED, null, "Auto retry after failed delivery"));
            // Rank khong doi (FAILED va ASSIGNED cung anh xa sang hang SHIPPED o cap order — muc 10.4),
            // goi de nhat quan nhung se no-op.
            orderService.recomputeAggregateStatus(delivery.getOrderId());
        } else {
            delivery.setStatus(DeliveryStatus.FAILED);
            deliveryRepository.save(delivery);
            // TODO (design doc v2 muc 10.5, chua trien khai): huy toan bo order thay vi chi phan cua
            // seller nay la interim gap cua giai doan 1 — se sua khi lam Cancel/Refund per-seller.
            orderService.markFailedDeliveryAndCancel(delivery.getOrderId(), delivery.getShipper());
        }
    }

    private DeliveryResponse toResponse(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getSellerId(),
                delivery.getShipper() != null ? delivery.getShipper().getId() : null,
                delivery.getShipper() != null ? delivery.getShipper().getFullName() : null,
                delivery.getStatus(),
                delivery.getFailureReason(),
                delivery.getRetryCount(),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt());
    }
}
