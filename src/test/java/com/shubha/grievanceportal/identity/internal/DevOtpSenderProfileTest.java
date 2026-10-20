package com.shubha.grievanceportal.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** The raw-code sender exists only under the dev-otp profile; the default never logs codes. */
class DevOtpSenderProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LoggingOtpSender.class, DevOtpSender.class);

    @Test
    void withoutTheProfileDevOtpSenderIsNotRegistered() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DevOtpSender.class);
            assertThat(context.getBean(OtpSender.class)).isInstanceOf(LoggingOtpSender.class);
        });
    }

    @Test
    void withTheProfileDevOtpSenderTakesOver() {
        runner.withPropertyValues("spring.profiles.active=dev-otp")
                .run(context -> assertThat(context.getBean(OtpSender.class)).isInstanceOf(DevOtpSender.class));
    }
}
