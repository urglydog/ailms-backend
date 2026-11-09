package com.lms.common.ratelimit;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hiệu năng/ổn định đợt 4: giới hạn số request theo IP bằng bộ đếm cửa sổ 1 phút trong Redis. */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> ops;
    @Mock private FilterChain chain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(ops);
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(true);
        props.setAuthPerMinute(5);
        props.setSearchPerMinute(10);
        props.setApiPerMinute(100);
        filter = new RateLimitFilter(new RateLimiter(redis), props);
    }

    private static MockHttpServletRequest request(String method, String uri, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    @Test
    void underLimit_passesThrough() throws Exception {
        when(ops.increment(anyString())).thenReturn(1L);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(request("POST", "/api/v1/auth/login", "1.2.3.4"), res, chain);

        verify(chain).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void loginOverLimit_returns429WithRetryAfter_andStopsChain() throws Exception {
        when(ops.increment(startsWith("rl:auth:1.2.3.4:"))).thenReturn(6L);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(request("POST", "/api/v1/auth/login", "1.2.3.4"), res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotBlank();
        assertThat(res.getContentAsString()).contains("429");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void firstHitInWindow_setsExpiry() throws Exception {
        when(ops.increment(anyString())).thenReturn(1L);

        filter.doFilter(request("GET", "/api/v1/courses", "9.9.9.9"), new MockHttpServletResponse(), chain);

        verify(redis).expire(startsWith("rl:search:9.9.9.9:"), any(Duration.class));
    }

    @Test
    void searchHasItsOwnLimit_separateFromGeneralApi() throws Exception {
        when(ops.increment(startsWith("rl:search:"))).thenReturn(11L);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(request("GET", "/api/v1/courses", "9.9.9.9"), res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void usesLastForwardedForEntry_setByOurProxy() throws Exception {
        when(ops.increment(anyString())).thenReturn(1L);
        MockHttpServletRequest req = request("POST", "/api/v1/auth/login", "172.18.0.5");
        // Client tự gửi "6.6.6.6" để giả IP; Caddy nối IP thật vào CUỐI header.
        req.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(ops).increment(startsWith("rl:auth:203.0.113.7:"));
    }

    @Test
    void nonApiPaths_andInternalApi_areNotLimited() throws Exception {
        filter.doFilter(request("GET", "/actuator/health", "1.1.1.1"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("POST", "/api/internal/dubbing/callback", "1.1.1.1"), new MockHttpServletResponse(), chain);

        verify(ops, never()).increment(anyString());
    }

    @Test
    void redisDown_failsOpen() throws Exception {
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(request("GET", "/api/v1/categories", "1.1.1.1"), res, chain);

        verify(chain).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void disabled_skipsCounting() throws Exception {
        RateLimitProperties off = new RateLimitProperties();
        off.setEnabled(false);
        RateLimitFilter disabled = new RateLimitFilter(new RateLimiter(redis), off);

        disabled.doFilter(request("POST", "/api/v1/auth/login", "1.1.1.1"), new MockHttpServletResponse(), chain);

        verify(ops, never()).increment(anyString());
        verify(chain).doFilter(any(), any());
    }
}
