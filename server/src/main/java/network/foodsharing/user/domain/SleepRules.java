package network.foodsharing.user.domain;

import java.time.LocalDate;

/** Sleep mode rules (A39, upstream computed {@code is_sleeping}) as pure functions. */
public final class SleepRules {

    private SleepRules() {
    }

    /** FULL, or TEMPORARY with {@code from ≤ today ≤ until}; a missing bound is open. */
    public static boolean isSleeping(SleepStatus status, LocalDate from, LocalDate until, LocalDate today) {
        if (status == SleepStatus.FULL) {
            return true;
        }
        if (status != SleepStatus.TEMPORARY) {
            return false;
        }
        return (from == null || !today.isBefore(from)) && (until == null || !today.isAfter(until));
    }

    /**
     * Valid input: TEMPORARY needs both dates, {@code from ≤ until} and {@code until ≥ today} (upstream requires a
     * period); NONE and FULL ignore the dates.
     */
    public static boolean validDates(SleepStatus status, LocalDate from, LocalDate until, LocalDate today) {
        if (status != SleepStatus.TEMPORARY) {
            return true;
        }
        return from != null && until != null && !from.isAfter(until) && !until.isBefore(today);
    }

    /** A TEMPORARY sleep that ended before {@code today} wakes up (maintenance job). */
    public static boolean expired(SleepStatus status, LocalDate until, LocalDate today) {
        return status == SleepStatus.TEMPORARY && until != null && until.isBefore(today);
    }
}
