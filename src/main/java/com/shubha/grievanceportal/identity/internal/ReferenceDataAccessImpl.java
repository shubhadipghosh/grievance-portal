package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.organization.ReferenceDataAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * An ACTIVE citizen sees every department and ward: they need them all to
 * file. Anyone else sees those their active grants cover, on any role; a grant
 * with a null axis covers everything on that axis.
 */
@Service
@Transactional(readOnly = true)
class ReferenceDataAccessImpl implements ReferenceDataAccess {

    private final AppUserRepository users;
    private final UserRoleGrantRepository grants;

    ReferenceDataAccessImpl(AppUserRepository users, UserRoleGrantRepository grants) {
        this.users = users;
        this.grants = grants;
    }

    @Override
    public Visible departmentsVisibleTo(UUID userId) {
        return visibleTo(userId, UserRoleGrantRepository.ScopeRow::getDepartmentId);
    }

    @Override
    public Visible wardsVisibleTo(UUID userId) {
        return visibleTo(userId, UserRoleGrantRepository.ScopeRow::getWardId);
    }

    private Visible visibleTo(UUID userId, Function<UserRoleGrantRepository.ScopeRow, UUID> axis) {
        AppUser user = users.findById(userId).orElse(null);
        if (user != null && user.getUserType() == UserType.CITIZEN && user.getStatus() == UserStatus.ACTIVE) {
            return Visible.ALL;
        }
        Set<UUID> ids = new HashSet<>();
        for (UserRoleGrantRepository.ScopeRow scope : grants.findActiveScopes(userId)) {
            UUID id = axis.apply(scope);
            if (id == null) {
                return Visible.ALL;
            }
            ids.add(id);
        }
        return new Visible(false, ids);
    }
}
