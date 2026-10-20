package com.shubha.grievanceportal.identity.internal;

/** Delivers a raw OTP to a phone. The only place the raw code ever goes. */
interface OtpSender {

    void send(String phone, String code);
}
