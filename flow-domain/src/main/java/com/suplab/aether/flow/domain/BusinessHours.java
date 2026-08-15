package com.suplab.aether.flow.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * A business-hours calendar for SLA clocks — the working window during which an approval SLA budget
 * is consumed. Time outside the window (nights, weekends, non-working days) does not count against a
 * deadline, so a task raised at 16:55 with a 30-minute budget is not "breached" five minutes into the
 * next morning; its clock resumes when the working window reopens.
 *
 * <p>Pure domain value — no framework, no persistence concerns. A tenant that configures no calendar
 * keeps the previous 24/7 behaviour (the SLA clock is plain wall time); this type is only consulted
 * when a {@link SlaPolicy} carries one.</p>
 *
 * @param zone        the time zone the {@code start}/{@code end} wall-clock times are interpreted in
 * @param start       the daily opening time (inclusive), strictly before {@code end}
 * @param end         the daily closing time (exclusive), strictly after {@code start}
 * @param workingDays the days the window is open (never empty)
 */
public record BusinessHours(ZoneId zone, LocalTime start, LocalTime end, Set<DayOfWeek> workingDays) {

    /** Safety cap on the day-advance loop — far beyond any realistic budget/window ratio. */
    private static final int MAX_DAY_HOPS = 100_000;

    public BusinessHours {
        if (zone == null) throw new IllegalArgumentException("zone required");
        if (start == null || end == null) throw new IllegalArgumentException("start and end required");
        if (!start.isBefore(end)) throw new IllegalArgumentException("start must be before end");
        if (workingDays == null || workingDays.isEmpty())
            throw new IllegalArgumentException("at least one working day required");
        // Defensive, order-independent copy.
        workingDays = EnumSet.copyOf(workingDays);
    }

    /**
     * The conventional default: Monday–Friday, 09:00–17:00, in the given zone.
     */
    public static BusinessHours standard(ZoneId zone) {
        return new BusinessHours(zone, LocalTime.of(9, 0), LocalTime.of(17, 0),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
    }

    /**
     * @return {@code true} if {@code instant} falls inside a working window.
     */
    public boolean isWithin(Instant instant) {
        var z = instant.atZone(zone);
        if (!workingDays.contains(z.getDayOfWeek())) return false;
        var t = z.toLocalTime();
        return !t.isBefore(start) && t.isBefore(end);
    }

    /**
     * Advances {@code budgetMinutes} of <em>working time</em> from {@code from}, skipping any minutes
     * that fall outside the working window, and returns the resulting deadline instant.
     *
     * <p>If {@code from} is outside the window the clock does not start until the next window opens;
     * whole non-working days are skipped. A non-positive budget returns {@code from} unchanged.</p>
     *
     * @param from          the instant the SLA clock starts
     * @param budgetMinutes minutes of working time to consume (&lt;= 0 → {@code from})
     * @return the instant by which the budget elapses within working hours
     */
    public Instant deadlineAfter(Instant from, int budgetMinutes) {
        if (budgetMinutes <= 0) return from;
        ZonedDateTime cursor = from.atZone(zone);
        long remaining = budgetMinutes;

        for (int hops = 0; hops < MAX_DAY_HOPS; hops++) {
            if (!workingDays.contains(cursor.getDayOfWeek())) {
                cursor = cursor.plusDays(1).with(start);
                continue;
            }
            ZonedDateTime windowStart = cursor.with(start);
            ZonedDateTime windowEnd = cursor.with(end);
            if (cursor.isBefore(windowStart)) {
                cursor = windowStart;
            }
            if (!cursor.isBefore(windowEnd)) {
                // At or past today's close — resume at the next day's opening.
                cursor = cursor.plusDays(1).with(start);
                continue;
            }
            long available = Duration.between(cursor, windowEnd).toMinutes();
            if (remaining <= available) {
                return cursor.plusMinutes(remaining).toInstant();
            }
            remaining -= available;
            cursor = cursor.plusDays(1).with(start);
        }
        // Unreachable for any sane configuration; fail safe to plain wall-clock rather than loop.
        return from.plus(Duration.ofMinutes(budgetMinutes));
    }
}
