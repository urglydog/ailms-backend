package com.lms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class FinanceConfirmationServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> ops;

    private FinanceConfirmationService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(ops);
        service = new FinanceConfirmationService(redis);
    }

    @Test
    void issue_storesOnlyTheHashOfTheTokenWithTtl_neverThePlainToken() {
        String token = service.issue(FinanceConfirmationService.TYPE_PAYOUT, 90L, 7L);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(ops).set(key.capture(), value.capture(), eq(Duration.ofMinutes(30)));
        assertThat(key.getValue()).startsWith("finance:confirm:").doesNotContain(token);
        assertThat(key.getValue()).isEqualTo("finance:confirm:" + FinanceConfirmationService.sha256(token));
        assertThat(value.getValue()).isEqualTo("PAYOUT:90:7");
        assertThat(token).hasSizeGreaterThanOrEqualTo(40).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void issue_producesDifferentTokensEachTime() {
        assertThat(service.issue("ACCOUNT", 1L, 1L)).isNotEqualTo(service.issue("ACCOUNT", 1L, 1L));
    }

    @Test
    void consume_validToken_returnsContentAndUsesAtomicGetAndDelete() {
        when(ops.getAndDelete("finance:confirm:" + FinanceConfirmationService.sha256("tok"))).thenReturn("ACCOUNT:12:5");

        Optional<FinanceConfirmationService.Confirmation> result = service.consume(" tok ");

        assertThat(result).contains(new FinanceConfirmationService.Confirmation("ACCOUNT", 12L, 5L));
    }

    @Test
    void consume_unknownExpiredOrAlreadyUsedToken_isEmpty() {
        when(ops.getAndDelete(anyString())).thenReturn(null);
        assertThat(service.consume("whatever")).isEmpty();
    }

    @Test
    void consume_blankOverlongOrMalformedValue_isEmptyWithoutBlindlyTrustingRedis() {
        assertThat(service.consume(null)).isEmpty();
        assertThat(service.consume("  ")).isEmpty();
        assertThat(service.consume("x".repeat(101))).isEmpty();

        when(ops.getAndDelete(anyString())).thenReturn("khong-dung-dinh-dang");
        assertThat(service.consume("tok")).isEmpty();
        when(ops.getAndDelete(anyString())).thenReturn("PAYOUT:abc:7");
        assertThat(service.consume("tok2")).isEmpty();
    }
}
