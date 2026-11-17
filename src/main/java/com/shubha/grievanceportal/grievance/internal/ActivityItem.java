package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.HoldReasonType;
import com.shubha.grievanceportal.identity.UserType;

import java.time.OffsetDateTime;

/**
 * One entry in a ticket's activity feed: a comment or an event. Flat, with
 * {@code kind} saying which fields apply; the rest are null.
 *
 * <p>Staff free text (assignment and reroute reasons, most status reasons) is
 * withheld from viewers without GRIEVANCE_VIEW_INTERNAL, by the same rule as
 * {@link HistoryEntry#forCitizen}.
 *
 * @param actorName null only for a citizen who has not given a name
 * @param internal  COMMENT only: an internal remark, never sent to a citizen
 */
record ActivityItem(
        Kind kind,
        OffsetDateTime at,
        UserType actorType,
        String actorName,
        // COMMENT
        String body,
        Boolean internal,
        // STATUS
        GrievanceStatus fromStatus,
        GrievanceStatus toStatus,
        HoldReasonType holdReasonType,
        // STATUS, ASSIGNMENT, REROUTE
        String reason,
        // ASSIGNMENT
        String assigneeName,
        String previousAssigneeName,
        // REROUTE: each pair null when that axis did not change
        String fromDepartment,
        String toDepartment,
        String fromCategory,
        String toCategory) {

    enum Kind { COMMENT, STATUS, ASSIGNMENT, REROUTE }

    static ActivityItem comment(GrievanceComment c) {
        return new ActivityItem(Kind.COMMENT, c.getCreatedAt(), c.getAuthor().getUserType(),
                c.getAuthor().getFullName(), c.getBody(), c.isInternalOnly(),
                null, null, null, null, null, null, null, null, null, null);
    }

    static ActivityItem status(HistoryEntry h) {
        return new ActivityItem(Kind.STATUS, h.occurredAt(), h.actorType(), h.actorName(), null, null,
                h.fromStatus(), h.toStatus(), h.holdReasonType(), h.reason(),
                null, null, null, null, null, null);
    }

    static ActivityItem assignment(GrievanceAssignment a, String previousAssigneeName, boolean internal) {
        return new ActivityItem(Kind.ASSIGNMENT, a.getAssignedAt(), a.getAssignedBy().getUserType(),
                a.getAssignedBy().getFullName(), null, null, null, null, null,
                internal ? a.getReason() : null, a.getAssignee().getFullName(), previousAssigneeName,
                null, null, null, null);
    }

    static ActivityItem reroute(GrievanceReroute r, String fromCategory, String toCategory, boolean internal) {
        boolean departmentChanged = !r.getOldDepartment().getId().equals(r.getNewDepartment().getId());
        return new ActivityItem(Kind.REROUTE, r.getOccurredAt(), r.getActor().getUserType(),
                r.getActor().getFullName(), null, null, null, null, null,
                internal ? r.getReason() : null, null, null,
                departmentChanged ? r.getOldDepartment().getName() : null,
                departmentChanged ? r.getNewDepartment().getName() : null,
                fromCategory, toCategory);
    }
}
