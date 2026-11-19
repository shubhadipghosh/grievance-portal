package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;

import java.util.List;
import java.util.Objects;
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

/**
 * The legal status transitions, mirroring the table in STATE_MACHINE.md.
 * Any (from, to) pair not listed here is illegal.
 */
final class GrievanceTransitions {

    /**
     * Sentinel for transitions authorised by ownership of the grievance
     * rather than by a permission grant. Not a row in the permission table.
     */
    static final String CITIZEN_OWNER = "CITIZEN_OWNER";

    /** @param permissions holding any one of these authorises the transition */
    record Transition(GrievanceStatus from, GrievanceStatus to, Set<String> permissions, boolean reasonRequired) {

        Transition {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            permissions = Set.copyOf(permissions);
            if (permissions.isEmpty()) {
                throw new IllegalArgumentException("permissions must not be empty: " + from + " -> " + to);
            }
        }
    }

    private static final List<Transition> TRANSITIONS = List.of(
            /*  1 */ new Transition(SUBMITTED, ACKNOWLEDGED, Set.of("GRIEVANCE_TRIAGE"), false),
            /*  2 */ new Transition(SUBMITTED, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            /*  3 */ new Transition(ACKNOWLEDGED, ASSIGNED, Set.of("GRIEVANCE_ASSIGN"), false),
            /*  4 */ new Transition(ACKNOWLEDGED, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            /*  5 */ new Transition(ASSIGNED, IN_PROGRESS, Set.of("GRIEVANCE_WORK"), false),
            /*  6 */ new Transition(ASSIGNED, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            /*  7 */ new Transition(IN_PROGRESS, ON_HOLD, Set.of("GRIEVANCE_HOLD"), true),
            /*  8 */ new Transition(IN_PROGRESS, RESOLVED, Set.of("GRIEVANCE_WORK"), true),
            /*  9 */ new Transition(IN_PROGRESS, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            /* 10 */ new Transition(ON_HOLD, IN_PROGRESS, Set.of("GRIEVANCE_HOLD"), false),
            /* 11 */ new Transition(ON_HOLD, REJECTED, Set.of("GRIEVANCE_REJECT"), true),
            /* 12 */ new Transition(RESOLVED, CLOSED, Set.of(CITIZEN_OWNER, "GRIEVANCE_CLOSE", "GRIEVANCE_CLOSE_AUTO"), false),
            /* 13 */ new Transition(RESOLVED, REOPENED, Set.of(CITIZEN_OWNER), true),
            /* 14 */ new Transition(RESOLVED, IN_PROGRESS, Set.of("GRIEVANCE_REVERT"), true),
            /* 15 */ new Transition(REJECTED, CLOSED, Set.of(CITIZEN_OWNER, "GRIEVANCE_CLOSE", "GRIEVANCE_CLOSE_AUTO"), false),
            /* 16 */ new Transition(REJECTED, REOPENED, Set.of(CITIZEN_OWNER), true),
            /* 17 */ new Transition(REOPENED, ASSIGNED, Set.of("GRIEVANCE_ASSIGN"), false),
            /* 18 */ new Transition(REOPENED, REJECTED, Set.of("GRIEVANCE_REJECT"), true));

    private GrievanceTransitions() {
    }

    static Optional<Transition> find(GrievanceStatus from, GrievanceStatus to) {
        return TRANSITIONS.stream()
                .filter(t -> t.from() == from && t.to() == to)
                .findFirst();
    }

    /** Every transition out of the status, in table order. */
    static List<Transition> from(GrievanceStatus from) {
        return TRANSITIONS.stream().filter(t -> t.from() == from).toList();
    }
}
