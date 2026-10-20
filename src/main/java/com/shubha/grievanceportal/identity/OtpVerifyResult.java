package com.shubha.grievanceportal.identity;

import java.util.UUID;

public sealed interface OtpVerifyResult {

    record Verified(UUID userId) implements OtpVerifyResult {
    }

    /** Deliberately carries no reason; see {@link OtpService#verifyLoginOtp}. */
    record Failed() implements OtpVerifyResult {
    }
}
