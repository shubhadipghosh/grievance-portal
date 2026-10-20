package com.shubha.grievanceportal.identity;

/** Citizen login by phone OTP, with auto-registration on first successful verification. */
public interface OtpService {

    /**
     * Sends a login code, superseding any earlier live code for the phone.
     * Rate limits (resend cooldown, per-phone daily cap, per-IP hourly cap)
     * are reported as {@link OtpSendResult.RateLimited}, never silently.
     *
     * @param requestIp an IP literal (IPv4 or IPv6), never a hostname
     * @param userAgent nullable; truncated to 255 characters
     * @throws IllegalArgumentException requestIp is not an IP literal
     */
    OtpSendResult sendLoginOtp(String phone, String requestIp, String userAgent);

    /**
     * Checks a login code. Every failure (bad phone, no live code, expired,
     * wrong, burned) is the same {@link OtpVerifyResult.Failed}, so the result
     * reveals nothing about whether the phone has been used before.
     */
    OtpVerifyResult verifyLoginOtp(String phone, String code, String requestIp);
}
