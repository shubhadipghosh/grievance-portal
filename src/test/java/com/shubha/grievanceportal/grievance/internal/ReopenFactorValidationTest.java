package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceTransitionService;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * grievance.sla.reopen-factor is validated at startup: 0 < factor <= 1.0.
 * Runs a real context against the real property key, with mocked collaborators.
 */
class ReopenFactorValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(GrievanceRepository.class, () -> mock(GrievanceRepository.class))
            .withBean(GrievanceStatusHistoryRepository.class, () -> mock(GrievanceStatusHistoryRepository.class))
            .withBean(GrievanceAssignmentRepository.class, () -> mock(GrievanceAssignmentRepository.class))
            .withBean(GrievanceAuthorization.class, () -> mock(GrievanceAuthorization.class))
            .withBean(AuthorizationQuery.class, () -> mock(AuthorizationQuery.class))
            .withBean(EntityManager.class, () -> mock(EntityManager.class))
            .withBean(GrievanceTransitionServiceImpl.class);

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.5", "1.5", "NaN"})
    void startupFailsForAFactorOutsideTheRange(String factor) {
        runner.withPropertyValues("grievance.sla.reopen-factor=" + factor)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("0 < factor <= 1.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.5", "1.0"})
    void startupSucceedsForAFactorInTheRange(String factor) {
        runner.withPropertyValues("grievance.sla.reopen-factor=" + factor)
                .run(context -> assertThat(context).hasSingleBean(GrievanceTransitionService.class));
    }

    @Test
    void startupSucceedsWithTheDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(GrievanceTransitionService.class));
    }
}
