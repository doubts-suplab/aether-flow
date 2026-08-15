package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessHoursTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    private static java.time.Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC);
    }

    @Test
    void invalidConfigs_areRejected() {
        assertThatThrownBy(() -> new BusinessHours(null, LocalTime.of(9, 0), LocalTime.of(17, 0),
                EnumSet.of(DayOfWeek.MONDAY))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessHours(UTC, LocalTime.of(17, 0), LocalTime.of(9, 0),
                EnumSet.of(DayOfWeek.MONDAY)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("before");
        assertThatThrownBy(() -> new BusinessHours(UTC, LocalTime.of(9, 0), LocalTime.of(17, 0),
                EnumSet.noneOf(DayOfWeek.class)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("working day");
    }

    @Test
    void deadlineWithinSameDay_isPlainAddition() {
        var hours = BusinessHours.standard(UTC);
        // Wed 2026-08-19 10:00 + 120 working minutes = 12:00 same day (well inside 09:00–17:00).
        var due = hours.deadlineAfter(at(2026, 8, 19, 10, 0), 120);
        assertThat(due).isEqualTo(at(2026, 8, 19, 12, 0));
    }

    @Test
    void budgetSpillsToNextWorkingDay() {
        var hours = BusinessHours.standard(UTC); // 8h/day window
        // Wed 16:00 + 120 min: 1h left today (→17:00), 1h carried to Thu 09:00 → 10:00.
        var due = hours.deadlineAfter(at(2026, 8, 19, 16, 0), 120);
        assertThat(due).isEqualTo(at(2026, 8, 20, 10, 0));
    }

    @Test
    void weekendIsSkipped() {
        var hours = BusinessHours.standard(UTC);
        // Fri 2026-08-21 16:30 + 60 min: 30 min to 17:00 Fri, remaining 30 min → Mon 09:30
        // (Sat/Sun skipped entirely).
        var due = hours.deadlineAfter(at(2026, 8, 21, 16, 30), 60);
        assertThat(due).isEqualTo(at(2026, 8, 24, 9, 30));
    }

    @Test
    void startBeforeWindow_clocksFromOpening() {
        var hours = BusinessHours.standard(UTC);
        // Tue 07:00 (before open) + 30 min → clock starts at 09:00 → 09:30.
        var due = hours.deadlineAfter(at(2026, 8, 18, 7, 0), 30);
        assertThat(due).isEqualTo(at(2026, 8, 18, 9, 30));
    }

    @Test
    void afterCloseAndOnNonWorkingDay_resumeNextOpening() {
        var hours = BusinessHours.standard(UTC);
        // Sat 12:00 (non-working) + 15 min → Mon 09:15.
        var due = hours.deadlineAfter(at(2026, 8, 22, 12, 0), 15);
        assertThat(due).isEqualTo(at(2026, 8, 24, 9, 15));
    }

    @Test
    void nonPositiveBudget_returnsStart() {
        var hours = BusinessHours.standard(UTC);
        var start = at(2026, 8, 19, 10, 0);
        assertThat(hours.deadlineAfter(start, 0)).isEqualTo(start);
        assertThat(hours.deadlineAfter(start, -30)).isEqualTo(start);
    }

    @Test
    void isWithin_reflectsWindow() {
        var hours = BusinessHours.standard(UTC);
        assertThat(hours.isWithin(at(2026, 8, 19, 10, 0))).isTrue();   // Wed 10:00
        assertThat(hours.isWithin(at(2026, 8, 19, 17, 0))).isFalse();  // Wed 17:00 (end exclusive)
        assertThat(hours.isWithin(at(2026, 8, 19, 8, 0))).isFalse();   // Wed 08:00 (before open)
        assertThat(hours.isWithin(at(2026, 8, 22, 10, 0))).isFalse();  // Sat 10:00 (weekend)
    }
}
