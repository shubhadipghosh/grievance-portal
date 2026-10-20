package com.shubha.grievanceportal.identity;

import java.time.Duration;

public sealed interface OtpSendResult {

    record Sent() implements OtpSendResult {
    }

    /** No code was sent; a send is allowed again after {@code retryAfter}. */
    record RateLimited(Duration retryAfter) implements OtpSendResult {
    }

    /** Not a plausible Indian mobile number in E.164 form (+91, then 10 digits starting 6-9). */
    record InvalidPhone() implements OtpSendResult {
    }
}
