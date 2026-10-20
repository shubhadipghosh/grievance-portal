package com.shubha.grievanceportal.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TODO: placeholder until SMS is available (waits on DLT registration). Logs
 * that a code was sent, with the phone masked, and never the code itself.
 */
@Component
class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(String phone, String code) {
        log.info("OTP sent to {} (code withheld; no SMS sender configured)", mask(phone));
    }

    private static String mask(String phone) {
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
