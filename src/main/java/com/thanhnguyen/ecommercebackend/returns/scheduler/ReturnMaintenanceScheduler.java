package com.thanhnguyen.ecommercebackend.returns.scheduler;

import com.thanhnguyen.ecommercebackend.returns.service.ReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReturnMaintenanceScheduler {

    private final ReturnService returnService;

    @Scheduled(fixedRate = 3_600_000)
    public void autoExpireApprovedReturns() {
        returnService.autoExpireApprovedReturns();
    }
}
