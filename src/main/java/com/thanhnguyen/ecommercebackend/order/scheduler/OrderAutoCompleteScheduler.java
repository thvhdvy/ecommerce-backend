package com.thanhnguyen.ecommercebackend.order.scheduler;

import com.thanhnguyen.ecommercebackend.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAutoCompleteScheduler {

    private final OrderService orderService;

    @Scheduled(fixedRate = 3_600_000)
    public void autoCompleteDeliveredOrders() {
        orderService.autoCompleteDeliveredOrders();
    }
}
