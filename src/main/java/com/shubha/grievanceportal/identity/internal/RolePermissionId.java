package com.shubha.grievanceportal.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RolePermissionId implements Serializable {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_code", nullable = false, length = 80)
    private String permissionCode;

    protected RolePermissionId() {
    }

    public RolePermissionId(UUID roleId, String permissionCode) {
        this.roleId = roleId;
        this.permissionCode = permissionCode;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RolePermissionId other)) {
            return false;
        }
        return Objects.equals(roleId, other.roleId) && Objects.equals(permissionCode, other.permissionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, permissionCode);
    }

    @Override
    public String toString() {
        return roleId + "/" + permissionCode;
    }
}
