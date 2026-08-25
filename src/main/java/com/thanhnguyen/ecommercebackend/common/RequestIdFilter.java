package com.thanhnguyen.ecommercebackend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gan 1 requestId (tu header X-Request-Id neu client da co, hoac tu sinh UUID) vao MDC cho moi request,
 * de log JSON (xem logback-spring.xml) co the correlate toan bo log line cua cung 1 request khi debug.
 * KHONG @Component - duoc khoi tao va dang ky thu cong trong WebFilterConfig voi Ordered.HIGHEST_PRECEDENCE
 * (chay truoc ca Spring Security filter chain) de tranh Spring Boot auto-register 2 lan.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER_NAME = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
