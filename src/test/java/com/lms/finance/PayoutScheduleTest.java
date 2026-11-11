package com.lms.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PayoutScheduleTest {

    @Test
    void nextPayoutDate_isAlwaysStrictlyAfterToday() {
        assertThat(PayoutSchedule.nextPayoutDate(LocalDate.of(2026, 11, 11), "5,20")).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(PayoutSchedule.nextPayoutDate(LocalDate.of(2026, 11, 5), "5,20")).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(PayoutSchedule.nextPayoutDate(LocalDate.of(2026, 11, 1), "5,20")).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(PayoutSchedule.nextPayoutDate(LocalDate.of(2026, 11, 20), "5,20")).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(PayoutSchedule.nextPayoutDate(LocalDate.of(2026, 11, 30), "5,20")).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void nextPayoutDate_rollsOverTheYear() {
        assertThat(PayoutSchedule.nextPayoutDate(LocalDate.of(2026, 12, 31), "5,20")).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void parse_sortsDedupesAndIgnoresInvalidDays_fallingBackToDefault() {
        assertThat(PayoutSchedule.parse("20, 5, 5")).containsExactly(5, 20);
        assertThat(PayoutSchedule.parse("29, 0, abc, 10")).containsExactly(10);
        assertThat(PayoutSchedule.parse("")).containsExactly(5, 20);
        assertThat(PayoutSchedule.parse(null)).containsExactly(5, 20);
        assertThat(PayoutSchedule.parse("abc,31")).containsExactly(5, 20);
    }

    @Test
    void singleDayPerMonth_works() {
        assertThat(PayoutSchedule.nextPayoutDate(LocalDate.of(2026, 11, 28), "15")).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(PayoutSchedule.nextPayoutDate(LocalDate.of(2026, 11, 10), "15")).isEqualTo(LocalDate.of(2026, 11, 15));
    }
}
