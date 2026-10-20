package com.shubha.grievanceportal.grievance;

import java.util.UUID;

/**
 * The only way to change a grievance's status. ON_HOLD and ASSIGNED need
 * extra input and have their own methods; passing either here is rejected.
 */
public interface GrievanceTransitionService {

    /**
     * @param reason nullable unless the transition requires one
     * @throws com.shubha.grievanceportal.shared.NotFoundException           no such grievance
     * @throws com.shubha.grievanceportal.shared.IllegalTransitionException  pair not in the table
     * @throws com.shubha.grievanceportal.shared.NotAuthorizedException      actor lacks permission or ownership
     * @throws com.shubha.grievanceportal.shared.ReasonRequiredException     reason required but blank
     * @throws com.shubha.grievanceportal.shared.ReopenLimitReachedException already reopened once
     */
    void transition(UUID grievanceId, GrievanceStatus target, UUID actorId, String reason);

    /**
     * Transitions 3 (ACKNOWLEDGED) and 17 (REOPENED) to ASSIGNED. The assignee
     * must be ACTIVE staff holding GRIEVANCE_WORK in the grievance's scope.
     *
     * @param reason nullable
     * @throws com.shubha.grievanceportal.shared.IneligibleAssigneeException assignee cannot take the work
     * @see #transition for the other failure modes
     */
    void assign(UUID grievanceId, UUID assigneeId, UUID actorId, String reason);
}
