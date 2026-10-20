package com.shubha.grievanceportal.identity.internal;

/** Why an OTP challenge is no longer usable. Mirrors otp_challenge.burned_reason. */
public enum BurnReason {
    MAX_ATTEMPTS,
    EXPIRED,
    SUPERSEDED,
    VERIFIED
}
