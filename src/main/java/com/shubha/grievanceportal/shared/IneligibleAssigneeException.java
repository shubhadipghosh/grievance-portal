package com.shubha.grievanceportal.shared;

/**
 * The proposed assignee cannot take the work: missing, not active staff, or
 * without GRIEVANCE_WORK in the grievance's scope.
 */
public class IneligibleAssigneeException extends RuntimeException {

    public IneligibleAssigneeException(Object assigneeId, String why) {
        super("Assignee " + assigneeId + " is not eligible: " + why);
    }
}
