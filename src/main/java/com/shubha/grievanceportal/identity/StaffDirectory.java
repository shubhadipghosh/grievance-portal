package com.shubha.grievanceportal.identity;

import java.util.List;
import java.util.UUID;

/** Who holds a role over a grievance's department and ward, for display. Not an authorization check. */
public interface StaffDirectory {

    /**
     * Full names of ACTIVE users holding the role through an active grant whose
     * scope covers the department and ward, sorted. Same scope rule as
     * {@link AuthorizationQuery#hasPermissionInScope}: a null axis is unrestricted.
     */
    List<String> namesWithRoleInScope(String roleCode, UUID departmentId, UUID wardId);
}
