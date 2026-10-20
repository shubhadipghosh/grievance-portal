package com.shubha.grievanceportal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GrievancePortalApplicationTests {

    @Autowired
    Environment environment;

    @Test
    void contextLoads() {
    }

    // The real configuration must never switch on a development-only profile.
    // Tests that need one opt in with @ActiveProfiles.

    @Test
    void devOtpProfileIsNotActive() {
        assertThat(environment.getActiveProfiles())
                .as("dev-otp is active in the default configuration. It writes RAW OTP CODES to the logs, "
                        + "so anyone who can read logs can log in as any citizen. Remove it from the "
                        + "configuration; do not delete this assertion.")
                .doesNotContain("dev-otp");
    }

    @Test
    void stubAuthProfileIsNotActive() {
        assertThat(environment.getActiveProfiles())
                .as("stub-auth is active in the default configuration. It leaves /api/v1/** UNAUTHENTICATED "
                        + "and trusts the X-Actor-Id header, so any caller can act as any user, admins "
                        + "included. Remove it from the configuration; do not delete this assertion.")
                .doesNotContain("stub-auth");
    }
}
