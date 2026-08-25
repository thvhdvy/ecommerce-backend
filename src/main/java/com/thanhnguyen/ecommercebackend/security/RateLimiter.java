package com.thanhnguyen.ecommercebackend.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter trong bo nho — du dung cho 1 instance app duy nhat (khong phai
 * distributed/multi-instance, se can Redis neu scale ngang sau nay). Dung de chan brute-force /
 * credential-stuffing tren login va lam dung forgot-password (xem AuthServiceImpl).
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> attemptsByKey = new ConcurrentHashMap<>();

    /**
     * @return true neu duoc phep thuc hien (va da ghi nhan attempt nay), false neu vuot qua gioi han.
     */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();
        Deque<Long> attempts = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst() < windowStart) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }
}
