package com.shubha.grievanceportal.identity;

import com.shubha.grievanceportal.identity.internal.AppUserRepository;
import com.shubha.grievanceportal.identity.internal.UserStatusHistory;
import com.shubha.grievanceportal.identity.internal.UserStatusHistoryRepository;
import com.shubha.grievanceportal.shared.IllegalTransitionException;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import com.shubha.grievanceportal.shared.ReasonRequiredException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * In this package rather than internal/ because it is the one caller of the
 * package-private {@link AppUser#setStatus}.
 */
@Service
@Transactional
class UserStatusServiceImpl implements UserStatusService {

    static final String USER_SUSPEND = "USER_SUSPEND";

    // Suspension is municipality-wide, so only a grant unrestricted on both axes counts.
    private static final PermissionScope UNSCOPED = new PermissionScope(null, null);

    private final AppUserRepository users;
    private final UserStatusHistoryRepository history;
    private final AuthorizationQuery authorizationQuery;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    UserStatusServiceImpl(AppUserRepository users, UserStatusHistoryRepository history,
                          AuthorizationQuery authorizationQuery,
                          FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.users = users;
        this.history = history;
        this.authorizationQuery = authorizationQuery;
        this.sessions = sessions;
    }

    @Override
    public void changeUserStatus(UUID targetUserId, UserStatus newStatus, UUID actorId, String reason) {
        Objects.requireNonNull(targetUserId, "targetUserId");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(actorId, "actorId");

        // Checked before the target is loaded, so an unauthorised caller learns nothing about it.
        if (!authorizationQuery.scopesWithPermission(actorId, USER_SUSPEND).contains(UNSCOPED)) {
            throw new NotAuthorizedException("Actor " + actorId + " may not change user status");
        }
        // Locking yourself out, or reinstating yourself, both need someone else.
        if (targetUserId.equals(actorId)) {
            throw new NotAuthorizedException("Actor " + actorId + " may not change their own status");
        }

        // Row-locked so concurrent changes serialize and each history row's from_status is true.
        AppUser target = users.findForUpdate(targetUserId)
                .orElseThrow(() -> new NotFoundException("User", targetUserId));
        if (target.getUserType() == UserType.SYSTEM) {
            throw new NotAuthorizedException("The SYSTEM user's status cannot be changed");
        }
        UserStatus from = target.getStatus();
        if (from == newStatus) {
            throw new IllegalTransitionException(from, newStatus);
        }
        if (reason == null || reason.isBlank()) {
            throw new ReasonRequiredException("A reason is required to change a user's status");
        }

        target.setStatus(newStatus);
        history.save(new UserStatusHistory(target, from, newStatus, users.getReferenceById(actorId), reason));

        // End their sessions too, rather than leaving them live but failing every check.
        // The principal name is the user id. Spring Session deletes in its own transaction.
        if (newStatus != UserStatus.ACTIVE) {
            sessions.findByPrincipalName(targetUserId.toString()).keySet().forEach(sessions::deleteById);
        }
    }
}
