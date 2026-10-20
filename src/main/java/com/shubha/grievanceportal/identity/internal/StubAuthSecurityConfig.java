package com.shubha.grievanceportal.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TODO(step 4, TEMPORARY): opens /api/v1/** so the X-Actor-Id header stub can
 * be exercised before real login exists. Anyone can act as anyone. Delete this
 * class when authentication is built.
 *
 * <p>Profile-gated so that forgetting it fails safe: without "stub-auth",
 * Spring Security's defaults apply and the API answers 401.
 */
@Configuration
@Profile("stub-auth")
class StubAuthSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(StubAuthSecurityConfig.class);

    @Bean
    SecurityFilterChain stubAuthFilterChain(HttpSecurity http) throws Exception {
        log.warn("stub-auth profile active: /api/v1/** is UNAUTHENTICATED and trusts the X-Actor-Id header");
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
