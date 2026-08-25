package com.thanhnguyen.ecommercebackend.order.scheduler;

import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Scheduled(fixedRate = 60_000)
    public void expirePendingPayments() {
        orderService.expirePendingPayments();
    }
}
