package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.internal.GrievanceTransitions.Transition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.shubha.grievanceportal.grievance.GrievanceStatus.ACKNOWLEDGED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.ASSIGNED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.CLOSED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.IN_PROGRESS;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.ON_HOLD;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.REJECTED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.REOPENED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.RESOLVED;
import static com.shubha.grievanceportal.grievance.GrievanceStatus.SUBMITTED;
import static com.shubha.grievanceportal.grievance.internal.GrievanceTransitions.CITIZEN_OWNER;
import static org.assertj.core.api.Assertions.assertThat;

class GrievanceTransitionsTest {

    // Transcribed independently from the table in STATE_MACHINE.md.
    private static final List<Transition> SPEC = List.of(
            new Transition(SUBMITTED, ACKNOWLEDGED, Set.of("GRIEVANCE_TRIAGE"), false),
            new Transition(SUBMITTED, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            new Transition(ACKNOWLEDGED, ASSIGNED, Set.of("GRIEVANCE_ASSIGN"), false),
            new Transition(ACKNOWLEDGED, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            new Transition(ASSIGNED, IN_PROGRESS, Set.of("GRIEVANCE_WORK"), false),
            new Transition(ASSIGNED, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            new Transition(IN_PROGRESS, ON_HOLD, Set.of("GRIEVANCE_HOLD"), true),
            new Transition(IN_PROGRESS, RESOLVED, Set.of("GRIEVANCE_WORK"), true),
            new Transition(IN_PROGRESS, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            new Transition(ON_HOLD, IN_PROGRESS, Set.of("GRIEVANCE_HOLD"), false),
            new Transition(ON_HOLD, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            new Transition(RESOLVED, CLOSED, Set.of(CITIZEN_OWNER, "GRIEVANCE_CLOSE", "GRIEVANCE_CLOSE_AUTO"), false),
            new Transition(RESOLVED, REOPENED, Set.of(CITIZEN_OWNER), true),
            new Transition(RESOLVED, IN_PROGRESS, Set.of("GRIEVANCE_REVERT"), true),
            new Transition(REJECTED, CLOSED, Set.of(CITIZEN_OWNER, "GRIEVANCE_CLOSE", "GRIEVANCE_CLOSE_AUTO"), false),
            new Transition(REJECTED, REOPENED, Set.of(CITIZEN_OWNER), true),
            new Transition(REOPENED, ASSIGNED, Set.of("GRIEVANCE_ASSIGN"), false),
            new Transition(REOPENED, REJECTED, Set.of("GRIEVANCE_REJECT"), true));

    @Test
    void exactlyTheEighteenSpecifiedPairsAreLegal() {
        int legal = 0;
        for (GrievanceStatus from : GrievanceStatus.values()) {
            for (GrievanceStatus to : GrievanceStatus.values()) {
                Optional<Transition> expected = SPEC.stream()
                        .filter(t -> t.from() == from && t.to() == to)
                        .findFirst();
                Optional<Transition> actual = GrievanceTransitions.find(from, to);

                assertThat(actual).as("%s -> %s", from, to).isEqualTo(expected);
                if (actual.isPresent()) {
                    legal++;
                }
            }
        }
        assertThat(GrievanceStatus.values()).hasSize(9);
        assertThat(legal).isEqualTo(18);
    }
}
