package com.lms.chat.service;

import com.lms.common.exception.QuotaExceededException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryQuotaServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @InjectMocks private DiscoveryQuotaService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "guestPerHour", 15L);
        ReflectionTestUtils.setField(service, "studentPerDay", 30L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void guest_firstMessage_setsOneHourExpiry() {
        when(valueOps.increment("discovery_quota:ip:1.2.3.4")).thenReturn(1L);
        assertThatCode(() -> service.consumeGuest("1.2.3.4")).doesNotThrowAnyException();
        verify(redisTemplate).expire("discovery_quota:ip:1.2.3.4", Duration.ofHours(1));
    }

    @Test
    void guest_atLimit_ok_overLimit_throws() {
        when(valueOps.increment(anyString())).thenReturn(15L, 16L);
        assertThatCode(() -> service.consumeGuest("1.2.3.4")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.consumeGuest("1.2.3.4")).isInstanceOf(QuotaExceededException.class);
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void student_overLimit_throwsWithMidnightHint() {
        when(valueOps.increment(anyString())).thenReturn(31L);
        assertThatThrownBy(() -> service.consumeStudent(7L))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("0h");
    }

    @Test
    void student_firstMessage_setsExpiry() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        assertThatCode(() -> service.consumeStudent(7L)).doesNotThrowAnyException();
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }
}
