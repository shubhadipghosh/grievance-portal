package com.shubha.grievanceportal.shared;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionProfileGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProductionProfileGuard.class);

    @Test
    void prodWithDevOtpFailsToStart() {
        runner.withPropertyValues("spring.profiles.active=prod,dev-otp")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("dev-otp")
                        .hasMessageContaining("raw OTP codes to the logs")
                        .hasMessageContaining("log in as any citizen"));
    }

    @Test
    void prodAloneStarts() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void devOtpAloneStarts() {
        // Local development.
        runner.withPropertyValues("spring.profiles.active=dev-otp")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void noProfilesStarts() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }
}
