package com.shubha.grievanceportal.shared;

import java.time.Duration;
import java.util.Objects;

/** Too many attempts; the caller may try again after {@link #getRetryAfter()}. */
public class RateLimitedException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super("Too many attempts. Try again later.");
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
