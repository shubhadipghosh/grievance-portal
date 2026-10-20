package com.shubha.grievanceportal.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LOCAL DEVELOPMENT ONLY: logs raw OTP codes so login works without SMS.
 *
 * <p>Profile-gated so that forgetting it fails safe: without "dev-otp",
 * {@link LoggingOtpSender} applies and codes never reach the logs. Primary so
 * it takes over from that default when the profile is on.
 */
@Component
@Primary
@Profile("dev-otp")
class DevOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(DevOtpSender.class);

    DevOtpSender() {
        log.warn("dev-otp profile active: RAW OTP CODES ARE BEING LOGGED. "
                + "Never enable this profile outside local development.");
    }

    @Override
    public void send(String phone, String code) {
        log.warn("*** DEV-OTP *** code {} for {}", code, phone);
    }
}
