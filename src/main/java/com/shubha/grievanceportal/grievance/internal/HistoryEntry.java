package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.HoldReasonType;
import com.shubha.grievanceportal.grievance.StatusChange;
import com.shubha.grievanceportal.identity.UserType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A status change as the API returns it. The citizen view is the safety rule
 * in STATE_MACHINE.md ("What citizens see"); do not loosen it here without
 * the stakeholder's decision.
 *
 * @param actorName null in the citizen view: officers are never named to citizens
 * @param reason    null when absent or withheld
 */
record HistoryEntry(
        GrievanceStatus fromStatus,
        GrievanceStatus toStatus,
        OffsetDateTime occurredAt,
        UserType actorType,
        String actorName,
        String reason,
        HoldReasonType holdReasonType) {

    /** Everything, for staff holding GRIEVANCE_VIEW_INTERNAL over the grievance. */
    static HistoryEntry full(StatusChange c) {
        return new HistoryEntry(c.fromStatus(), c.toStatus(), c.occurredAt(), c.actorType(), c.actorName(),
                c.reason(), c.holdReasonType());
    }

    /**
     * For anyone else: no names, and the free-text reason only where it is
     * written for the citizen. ON_HOLD shows its category, never its text.
     */
    static HistoryEntry forCitizen(StatusChange c, UUID viewerId) {
        boolean own = c.actorId().equals(viewerId);
        boolean showReason = switch (c.toStatus()) {
            case RESOLVED, REJECTED -> true;
            case REOPENED, CLOSED -> own;
            default -> false;
        };
        return new HistoryEntry(c.fromStatus(), c.toStatus(), c.occurredAt(), c.actorType(), null,
                showReason ? c.reason() : null, c.holdReasonType());
    }
}
