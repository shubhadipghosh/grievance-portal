package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import com.shubha.grievanceportal.identity.UserStatus;
import org.springframework.stereotype.Component;

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
}
