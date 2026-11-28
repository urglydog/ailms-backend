package com.lms.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Giới hạn số request theo IP (hiệu năng/ổn định đợt 4). Gắn vào chuỗi Spring Security sau CORS
 * nên response 429 vẫn có header CORS. Redis lỗi thì cho qua (fail-open) — rate limit không được
 * làm sập cả hệ thống.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final RateLimitProperties props;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !props.isEnabled() || !uri.startsWith("/api/") || uri.startsWith("/api/internal/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String bucket;
        int limit;
        if (uri.startsWith("/api/v1/auth/") && "POST".equalsIgnoreCase(method)) {
            bucket = "auth";
            limit = props.getAuthPerMinute();
        } else if ("GET".equalsIgnoreCase(method)
                && (uri.equals("/api/v1/courses") || uri.startsWith("/api/v1/courses/search"))) {
            bucket = "search";
            limit = props.getSearchPerMinute();
        } else {
            bucket = "api";
            limit = props.getApiPerMinute();
        }

        long retryAfter;
        try {
            retryAfter = limiter.hit(bucket, clientIp(request), limit);
        } catch (RuntimeException e) {
            log.warn("Rate limit skipped, Redis error: {}", e.getMessage());
            retryAfter = 0;
        }
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/problem+json;charset=UTF-8");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                    + "\"detail\":\"Bạn thao tác quá nhanh, vui lòng thử lại sau " + retryAfter + " giây.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * IP thật của client. Caddy NỐI IP nó thấy vào CUỐI {@code X-Forwarded-For}; phần đầu client tự
     * gửi được — nên lấy phần tử cuối để không bị giả IP lách giới hạn. Header chỉ được tin khi kết nối
     * đến từ proxy nội bộ (loopback / mạng riêng Docker); ai gọi thẳng từ IP public thì dùng IP kết nối.
     */
    public static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && fromTrustedProxy(remote)) {
            String[] parts = xff.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) return last;
        }
        return remote;
    }

    /** Chỉ nhận địa chỉ IP dạng số (không tra DNS): loopback, site-local (10/8, 172.16/12, 192.168/16), fc00::/7. */
    static boolean fromTrustedProxy(String remote) {
        if (remote == null || remote.isBlank() || !remote.matches("[0-9a-fA-F:.]+")) return false;
        try {
            java.net.InetAddress a = java.net.InetAddress.getByName(remote);
            if (a.isLoopbackAddress() || a.isSiteLocalAddress()) return true;
            byte[] b = a.getAddress();
            return b.length == 16 && (b[0] & 0xFE) == 0xFC;
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
