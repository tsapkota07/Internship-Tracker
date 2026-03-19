package com.tirsansapkota.internshiptracker.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter for auth endpoints.
 *
 * Implemented as a Servlet Filter (not a HandlerInterceptor) so it runs
 * before Spring Security's filter chain — which is necessary for /login,
 * because Spring Security's UsernamePasswordAuthenticationFilter handles
 * POST /login entirely within the filter chain and never reaches
 * DispatcherServlet or any HandlerInterceptor.
 *
 * /login           — 10 POST attempts per 5 minutes per IP
 * /forgot-password — 5  POST attempts per 15 minutes per IP
 *
 * Only applies to POST requests. GET requests pass through freely.
 */
@Component
@Order(-101) // must run before Spring Security's FilterChainProxy (order -100)
public class RateLimitInterceptor implements Filter {

    private static final int  LOGIN_MAX          = 10;
    private static final long LOGIN_WINDOW_SECS  = 300;  // 5 minutes

    private static final int  FORGOT_MAX         = 5;
    private static final long FORGOT_WINDOW_SECS = 900;  // 15 minutes

    private record Window(int count, long windowStart) {}

    private final Map<String, Window> loginBuckets  = new ConcurrentHashMap<>();
    private final Map<String, Window> forgotBuckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String path = request.getRequestURI();
            String ip   = resolveClientIp(request);

            if ("/login".equals(path) && isOverLimit(ip, loginBuckets, LOGIN_MAX, LOGIN_WINDOW_SECS)) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too many requests. Please wait a moment and try again.");
                return;
            }
            if ("/forgot-password".equals(path) && isOverLimit(ip, forgotBuckets, FORGOT_MAX, FORGOT_WINDOW_SECS)) {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too many requests. Please wait a moment and try again.");
                return;
            }
        }

        chain.doFilter(req, res);
    }

    private boolean isOverLimit(String ip,
                                Map<String, Window> buckets,
                                int max,
                                long windowSecs) {
        long now = Instant.now().getEpochSecond();

        Window current = buckets.compute(ip, (k, existing) -> {
            if (existing == null || (now - existing.windowStart()) >= windowSecs) {
                return new Window(1, now);
            }
            return new Window(existing.count() + 1, existing.windowStart());
        });

        return current.count() > max;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
