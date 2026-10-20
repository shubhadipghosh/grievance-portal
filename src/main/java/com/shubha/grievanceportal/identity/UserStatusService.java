package com.shubha.grievanceportal.identity;

import java.util.UUID;

/** Suspending and reinstating users. The only way a user's status changes. */
public interface UserStatusService {

    /**
     * Changes the target's status and records it in user_status_history.
     * Status gates authorization on every path, so this takes effect on the
     * target's next request. Any status other than ACTIVE also ends the
     * target's sessions.
     *
     * @throws com.shubha.grievanceportal.shared.NotAuthorizedException the actor
     *         lacks unscoped USER_SUSPEND, the target is the actor, or the target is SYSTEM
     * @throws com.shubha.grievanceportal.shared.NotFoundException no such target user
     * @throws com.shubha.grievanceportal.shared.IllegalTransitionException the target already has newStatus
     * @throws com.shubha.grievanceportal.shared.ReasonRequiredException reason is null or blank
     */
    void changeUserStatus(UUID targetUserId, UserStatus newStatus, UUID actorId, String reason);
}
