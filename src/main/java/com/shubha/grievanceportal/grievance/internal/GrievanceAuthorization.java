package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import com.shubha.grievanceportal.identity.PermissionScope;
import com.shubha.grievanceportal.identity.UserStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.shubha.grievanceportal.grievance.internal.GrievanceTransitions.CITIZEN_OWNER;

/**
 * Decides whether an actor may perform an action on a grievance. Holding any
 * one of the accepted permissions suffices. {@code CITIZEN_OWNER} is satisfied
 * only by ownership and is never looked up as a permission.
 */
@Component
class GrievanceAuthorization {

    static final String VIEW = "GRIEVANCE_VIEW";
    static final String VIEW_INTERNAL = "GRIEVANCE_VIEW_INTERNAL";

    /**
     * Wards of one department whose grievances an actor sees through
     * GRIEVANCE_VIEW: all of them, or those listed. Their own grievances,
     * as an ACTIVE citizen, are visible regardless.
     */
    record VisibleWards(boolean all, Set<UUID> wardIds) {
        static final VisibleWards ALL = new VisibleWards(true, Set.of());
    }

    private final AuthorizationQuery authorizationQuery;

    GrievanceAuthorization(AuthorizationQuery authorizationQuery) {
        this.authorizationQuery = authorizationQuery;
    }

    boolean isAuthorized(UUID actorId, Grievance grievance, Set<String> accepted) {
        AppUser citizen = grievance.getCitizen();
        if (accepted.contains(CITIZEN_OWNER) && citizen.getId().equals(actorId)) {
            // A locked or disabled citizen cannot act on their own grievance.
            return citizen.getStatus() == UserStatus.ACTIVE;
        }
        Set<String> permissions = accepted.stream()
                .filter(code -> !code.equals(CITIZEN_OWNER))
                .collect(Collectors.toUnmodifiableSet());
        if (permissions.isEmpty()) {
            return false;
        }
        return authorizationQuery.hasPermissionInScope(actorId, permissions,
                grievance.getDepartment().getId(), grievance.getWard().getId());
    }

    /** The ACTIVE citizen who filed it, or GRIEVANCE_VIEW with scope covering it. */
    boolean canView(UUID actorId, Grievance grievance) {
        return isAuthorized(actorId, grievance, Set.of(CITIZEN_OWNER, VIEW));
    }

    /** GRIEVANCE_VIEW_INTERNAL with scope covering it: unredacted history. Citizens never hold it. */
    boolean canViewInternal(UUID actorId, Grievance grievance) {
        return isAuthorized(actorId, grievance, Set.of(VIEW_INTERNAL));
    }

    /** {@link #canView} for a whole department at once, so a list can be filtered in its query. */
    VisibleWards visibleWards(UUID actorId, UUID departmentId) {
        Set<UUID> wardIds = new HashSet<>();
        for (PermissionScope scope : authorizationQuery.scopesWithPermission(actorId, VIEW)) {
            if (scope.departmentId() != null && !scope.departmentId().equals(departmentId)) {
                continue;
            }
            if (scope.wardId() == null) {
                return VisibleWards.ALL;
            }
            wardIds.add(scope.wardId());
        }
        return new VisibleWards(false, Set.copyOf(wardIds));
    }
}
