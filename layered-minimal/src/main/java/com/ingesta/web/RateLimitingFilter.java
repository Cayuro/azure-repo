package com.ingesta.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.config.RateLimitProperties;
import com.ingesta.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Deque<Long>> requestsByOrigin = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith(properties.getPathPrefix());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String origin = resolveOrigin(request);
        long now = System.currentTimeMillis();
        long windowMillis = properties.getWindowSeconds() * 1000L;

        Deque<Long> requests = requestsByOrigin.computeIfAbsent(origin, ignored -> new ArrayDeque<>());
        int remaining;
        long resetSeconds;
        synchronized (requests) {
            prune(requests, now - windowMillis);
            if (requests.size() >= properties.getMaxRequests()) {
                long oldest = requests.peekFirst() == null ? now : requests.peekFirst();
                resetSeconds = Math.max(1L, (windowMillis - (now - oldest) + 999L) / 1000L);
                writeHeaders(response, 0, resetSeconds);
                response.setHeader("Retry-After", String.valueOf(resetSeconds));
                response.setStatus(429);
                response.setContentType("application/json");
                objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                    429,
                        "Too Many Requests",
                        "Se excedio el limite de peticiones permitido",
                        java.util.List.of("Espere " + resetSeconds + " segundos antes de volver a intentar")));
                return;
            }
            requests.addLast(now);
            prune(requests, now - windowMillis);
            remaining = Math.max(0, properties.getMaxRequests() - requests.size());
            long oldest = requests.peekFirst() == null ? now : requests.peekFirst();
            resetSeconds = Math.max(1L, (windowMillis - (now - oldest) + 999L) / 1000L);
        }

        writeHeaders(response, remaining, resetSeconds);
        filterChain.doFilter(request, response);
    }

    private void writeHeaders(HttpServletResponse response, int remaining, long resetSeconds) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.getMaxRequests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(Instant.now().getEpochSecond() + resetSeconds));
    }

    private void prune(Deque<Long> requests, long oldestAllowed) {
        while (!requests.isEmpty() && requests.peekFirst() < oldestAllowed) {
            requests.removeFirst();
        }
    }

    private String resolveOrigin(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}