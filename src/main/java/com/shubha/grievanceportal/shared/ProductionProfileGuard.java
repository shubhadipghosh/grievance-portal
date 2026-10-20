package com.shubha.grievanceportal.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start when a development-only profile is active alongside "prod".
 * GrievancePortalApplicationTests catches the repo's own configuration switching
 * one on; this catches one set on the server, e.g. through SPRING_PROFILES_ACTIVE.
 *
 * <p>Checked while beans are created rather than on ApplicationReadyEvent, so
 * the web server never starts listening. Any future development-only profile
 * belongs here too.
 */
@Configuration
class ProductionProfileGuard {

    ProductionProfileGuard(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod & dev-otp"))) {
            throw new IllegalStateException("Refusing to start: dev-otp is active alongside prod. "
                    + "It writes raw OTP codes to the logs, so anyone who can read logs can log in "
                    + "as any citizen. Remove dev-otp from the active profiles.");
        }
    }
}
