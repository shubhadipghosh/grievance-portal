package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AuthorizationQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class AuthorizationQueryImpl implements AuthorizationQuery {

    private final UserRoleGrantRepository grants;

    AuthorizationQueryImpl(UserRoleGrantRepository grants) {
        this.grants = grants;
    }

    @Override
    public boolean hasPermissionInScope(UUID userId, Set<String> permissionCodes,
                                        UUID departmentId, UUID wardId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(departmentId, "departmentId");
        Objects.requireNonNull(wardId, "wardId");
        // IN () is invalid SQL, and holding none of nothing is never authorised.
        if (permissionCodes.isEmpty()) {
            return false;
        }
        return grants.hasPermissionInScope(userId, permissionCodes, departmentId, wardId);
    }
}
