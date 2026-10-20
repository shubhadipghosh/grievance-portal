package com.shubha.grievanceportal.grievance.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** The ticket number's year follows the display zone, not UTC. */
class TicketNumberTest {

    @Test
    void lateOnNewYearsEveInIstGetsTheIstYear() {
        // 23:00 IST on 31 Dec 2026 is 17:30 UTC the same day, so these agree.
        Instant beforeMidnightIst = Instant.parse("2026-12-31T17:30:00Z");
        assertThat(GrievanceServiceImpl.ticketNo(beforeMidnightIst, ZoneId.of("Asia/Kolkata"), 123))
                .isEqualTo("GRV-2026-000123");
    }

    @Test
    void earlyOnNewYearsDayInIstGetsTheIstYearNotUtc() {
        // 00:30 IST on 1 Jan 2027 is still 31 Dec 2026 in UTC.
        Instant afterMidnightIst = Instant.parse("2026-12-31T19:00:00Z");
        assertThat(GrievanceServiceImpl.ticketNo(afterMidnightIst, ZoneId.of("Asia/Kolkata"), 123))
                .isEqualTo("GRV-2027-000123");
    }
}
