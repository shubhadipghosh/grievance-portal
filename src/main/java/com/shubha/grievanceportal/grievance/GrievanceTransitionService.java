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
}
