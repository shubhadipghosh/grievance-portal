package com.shubha.grievanceportal.identity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Scoped permission checks against a user's active role grants. */
public interface AuthorizationQuery {

    /**
     * True if the user is ACTIVE and holds at least one of the permissions through
     * an active grant whose scope covers the department and ward. A grant with a null
     * department or ward is unrestricted on that axis.
     */
    boolean hasPermissionInScope(UUID userId, Set<String> permissionCodes,
                                 UUID departmentId, UUID wardId);

    /**
     * Every scope in which the user holds the permission through an active
     * grant; empty if the user is not ACTIVE. For filtering a list in its query,
     * where checking rows one at a time with {@link #hasPermissionInScope}
     * would break paging.
     */
    List<PermissionScope> scopesWithPermission(UUID userId, String permissionCode);
}
