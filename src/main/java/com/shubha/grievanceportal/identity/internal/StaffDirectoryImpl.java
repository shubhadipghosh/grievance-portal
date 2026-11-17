package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.StaffDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class StaffDirectoryImpl implements StaffDirectory {

    private final UserRoleGrantRepository grants;

    StaffDirectoryImpl(UserRoleGrantRepository grants) {
        this.grants = grants;
    }

    @Override
    public List<String> namesWithRoleInScope(String roleCode, UUID departmentId, UUID wardId) {
        Objects.requireNonNull(roleCode, "roleCode");
        Objects.requireNonNull(departmentId, "departmentId");
        Objects.requireNonNull(wardId, "wardId");
        return grants.findNamesWithRoleInScope(roleCode, departmentId, wardId);
    }
}
