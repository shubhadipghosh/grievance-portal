package com.shubha.grievanceportal.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** OTP policy under otp.* (documented in application.yml). Validated at startup. */
@ConfigurationProperties("otp")
record OtpProperties(
        @DefaultValue("6") int codeLength,
        @DefaultValue("15m") Duration validity,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("60s") Duration resendCooldown,
        @DefaultValue("5") int maxSendsPerPhonePerDay,
        @DefaultValue("20") int maxSendsPerIpPerHour) {

    OtpProperties {
        // The code is drawn as an int below 10^codeLength.
        if (codeLength < 4 || codeLength > 9) {
            throw new IllegalArgumentException("otp.code-length must be 4..9, was " + codeLength);
        }
        // otp_challenge.attempts_capped allows 0..3.
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("otp.max-attempts must be 1..3, was " + maxAttempts);
        }
    }
}
