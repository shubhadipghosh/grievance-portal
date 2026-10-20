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

    /**
     * Transition 7 (IN_PROGRESS to ON_HOLD). Only AWAITING_CITIZEN pauses the SLA
     * clock, and it requires owner type CITIZEN.
     * <ul>
     *   <li>CITIZEN: the grievance's own citizen; holdOwnerId and holdOwnerNote must be null</li>
     *   <li>STAFF: holdOwnerId of an ACTIVE staff user; holdOwnerNote optional</li>
     *   <li>EXTERNAL: holdOwnerNote names the party; holdOwnerId must be null</li>
     * </ul>
     *
     * @throws com.shubha.grievanceportal.shared.InvalidHoldDetailsException owner details do not fit
     * @see #transition for the other failure modes
     */
    void placeOnHold(UUID grievanceId, HoldReasonType reasonType, HoldOwnerType ownerType,
                     UUID holdOwnerId, String holdOwnerNote, UUID actorId, String reason);
}
