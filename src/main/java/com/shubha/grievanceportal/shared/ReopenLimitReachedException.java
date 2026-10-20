package com.shubha.grievanceportal.shared;

/** A grievance may be reopened only once. */
public class ReopenLimitReachedException extends RuntimeException {

    public ReopenLimitReachedException(Object grievanceId) {
        super("Grievance " + grievanceId + " has already been reopened once");
    }
}
