package com.shubha.grievanceportal.grievance;

/**
 * Lifecycle states. Allowed moves between them are defined by the transition
 * table, not here.
 */
public enum GrievanceStatus {
    SUBMITTED,
    ACKNOWLEDGED,
    ASSIGNED,
    IN_PROGRESS,
    ON_HOLD,
    RESOLVED,
    CLOSED,
    REJECTED,
    REOPENED
}
