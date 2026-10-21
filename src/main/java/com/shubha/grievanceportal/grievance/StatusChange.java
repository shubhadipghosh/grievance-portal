package com.shubha.grievanceportal.grievance;

import com.shubha.grievanceportal.identity.UserType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of a grievance's status history, unfiltered. What a citizen may see
 * of it is decided at the API (STATE_MACHINE.md, "What citizens see").
 *
 * @param fromStatus     null on the initial SUBMITTED row
 * @param reason         nullable
 * @param holdReasonType set only on ON_HOLD rows, and null on those written before V10
 */
public record StatusChange(
        GrievanceStatus fromStatus,
        GrievanceStatus toStatus,
        OffsetDateTime occurredAt,
        UUID actorId,
        UserType actorType,
        String actorName,
        String reason,
        HoldReasonType holdReasonType) {
}
