package com.shubha.grievanceportal.identity;

import java.util.List;
import java.util.UUID;

/** Who holds a role or permission over a grievance's department and ward. Not an authorization check. */
public interface StaffDirectory {

    /**
     * Full names of ACTIVE users holding the role through an active grant whose
     * scope covers the department and ward, sorted. Same scope rule as
     * {@link AuthorizationQuery#hasPermissionInScope}: a null axis is unrestricted.
     */
    List<String> namesWithRoleInScope(String roleCode, UUID departmentId, UUID wardId);

    /**
     * ACTIVE staff holding the permission through an active grant whose scope
     * covers the department and ward, sorted by name. Same scope rule as
     * {@link AuthorizationQuery#hasPermissionInScope}.
     */
    List<StaffMember> staffWithPermissionInScope(String permissionCode, UUID departmentId, UUID wardId);
}
