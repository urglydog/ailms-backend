package com.lms.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Xác thực AI Worker gọi callback qua {@code /api/internal/**} bằng secret dùng chung
 * (header {@code X-Internal-Token}), KHÔNG dùng JWT — AI Worker không có tài khoản người
 * dùng. Secret khớp {@code INTERNAL_API_TOKEN} ở cả {@code be/.env} và {@code ai-worker/.env}
 * (bí mật hạ tầng dùng chung, xem docker-compose.yml).
 */
@Component
public class InternalApiTokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";

    @Value("${lms.internal.api-token}")
    private String expectedToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided, expectedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"INTERNAL_TOKEN_INVALID\",\"detail\":\"Thieu hoac sai X-Internal-Token\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
