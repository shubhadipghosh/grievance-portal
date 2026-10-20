package com.shubha.grievanceportal.grievance;

/**
 * Why a grievance is ON_HOLD. Only {@link #AWAITING_CITIZEN} pauses the SLA
 * clock; every other reason is the municipality's own problem.
 */
public enum HoldReasonType {
    AWAITING_CITIZEN,
    AWAITING_CONTRACTOR,
    AWAITING_DEPARTMENT,
    AWAITING_BUDGET,
    OTHER
}
