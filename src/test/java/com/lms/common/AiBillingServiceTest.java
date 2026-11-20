package com.lms.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.repository.UserRepository;
import com.lms.common.entity.AiBillingEntry;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.repository.AiBillingEntryRepository;
import com.lms.common.service.AiBillingService;
import com.lms.common.service.AiBillingService.CreateReq;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Nhập hóa đơn nhà cung cấp AI để đối soát: kiểm tra hợp lệ và chặn chồng lấn. */
@ExtendWith(MockitoExtension.class)
class AiBillingServiceTest {

    @Mock private AiBillingEntryRepository repository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AiBillingService service;

    private static final LocalDate A = LocalDate.of(2026, 11, 1);
    private static final LocalDate B = LocalDate.of(2026, 11, 20);

    @Test
    void create_savesAValidEntry() {
        when(repository.countOverlapping(A, B)).thenReturn(0L);
        when(userRepository.findByEmail("admin@lms.local")).thenReturn(Optional.empty());
        when(repository.save(any(AiBillingEntry.class))).thenAnswer(i -> i.getArgument(0));

        var res = service.create("admin@lms.local", new CreateReq(A, B, 220_739L, " Google AI Studio ", " ghi chú "));

        assertThat(res.amountVnd()).isEqualTo(220_739L);
        assertThat(res.source()).isEqualTo("Google AI Studio");
        assertThat(res.note()).isEqualTo("ghi chú");
    }

    @Test
    void create_rejectsEveryInvalidInputWithoutSaving() {
        assertThatThrownBy(() -> service.create("a", null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(null, B, 1L, "x", null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(B, A, 1L, "x", null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(A, A.plusDays(401), 1L, "x", null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(A, LocalDate.now().plusDays(5), 1L, "x", null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(A, B, -1L, "x", null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(A, B, null, "x", null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(A, B, 5_000_000_001L, "x", null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(A, B, 1L, "  ", null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(A, B, 1L, "x".repeat(101), null))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create("a", new CreateReq(A, B, 1L, "x", "y".repeat(301)))).isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsAPeriodThatOverlapsAnExistingEntry() {
        when(repository.countOverlapping(A, B)).thenReturn(1L);

        assertThatThrownBy(() -> service.create("a", new CreateReq(A, B, 1000L, "Google", null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("trùng");
        verify(repository, never()).save(any());
    }
}
