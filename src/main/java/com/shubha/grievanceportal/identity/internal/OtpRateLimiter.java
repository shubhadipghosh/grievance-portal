package com.shubha.grievanceportal.identity.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Send counters in otp_rate_limit over fixed windows aligned to the epoch
 * (the daily window starts at 00:00 UTC). Runs in the caller's transaction.
 */
@Component
class OtpRateLimiter {

    enum Scope { PHONE, IP }

    private final JdbcClient jdbc;

    OtpRateLimiter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Counts one send, or returns how long until one is allowed. One upsert
     * both checks and increments, and the row stays locked until the caller
     * commits, so concurrent sends for one key serialize and cannot both pass.
     *
     * @param cooldown minimum gap between sends, zero for none. Kept in
     *                 blocked_until, so it does not carry into a new window.
     */
    Optional<Duration> tryAcquire(Scope scope, String key, Duration window, int cap, Duration cooldown,
                                  Instant now) {
        OffsetDateTime windowStart = windowStart(now, window);
        OffsetDateTime at = now.atOffset(ZoneOffset.UTC);
        boolean acquired = jdbc.sql("""
                        INSERT INTO otp_rate_limit (scope_type, scope_key, window_start, send_count, blocked_until)
                        VALUES (:scope, :key, :windowStart, 1, CAST(:blockedUntil AS timestamptz))
                        ON CONFLICT (scope_type, scope_key, window_start) DO UPDATE
                            SET send_count = otp_rate_limit.send_count + 1,
                                blocked_until = EXCLUDED.blocked_until
                            WHERE otp_rate_limit.send_count < :cap
                              AND (otp_rate_limit.blocked_until IS NULL OR otp_rate_limit.blocked_until <= :now)
                        RETURNING send_count
                        """)
                .param("scope", scope.name())
                .param("key", key)
                .param("windowStart", windowStart)
                .param("blockedUntil", cooldown.isZero() ? null : at.plus(cooldown))
                .param("cap", cap)
                .param("now", at)
                .query(Integer.class)
                .optional()
                .isPresent();
        if (acquired) {
            return Optional.empty();
        }

        // Blocked, so the row exists (and the upsert holds its lock).
        return Optional.of(jdbc.sql("""
                        SELECT send_count, blocked_until FROM otp_rate_limit
                        WHERE scope_type = :scope AND scope_key = :key AND window_start = :windowStart
                        """)
                .param("scope", scope.name())
                .param("key", key)
                .param("windowStart", windowStart)
                .query((rs, n) -> {
                    Duration retry = Duration.ZERO;
                    if (rs.getInt("send_count") >= cap) {
                        retry = Duration.between(at, windowStart.plus(window));
                    }
                    OffsetDateTime blockedUntil = rs.getObject("blocked_until", OffsetDateTime.class);
                    if (blockedUntil != null && blockedUntil.isAfter(at)) {
                        Duration cooling = Duration.between(at, blockedUntil);
                        retry = cooling.compareTo(retry) > 0 ? cooling : retry;
                    }
                    return retry;
                })
                .single());
    }

    private static OffsetDateTime windowStart(Instant now, Duration window) {
        long seconds = window.toSeconds();
        return Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), seconds) * seconds)
                .atOffset(ZoneOffset.UTC);
    }
}
