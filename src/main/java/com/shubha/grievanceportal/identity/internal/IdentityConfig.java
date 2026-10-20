package com.shubha.grievanceportal.identity.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(OtpProperties.class)
class IdentityConfig {

    /**
     * One encoder for staff passwords and OTP hashes. Delegating, so stored
     * hashes carry their algorithm ({bcrypt}...) and can be upgraded later.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
