package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.Role;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Join entity for role_permission. Mapped explicitly rather than as a
 * {@code @ManyToMany} on Role so the public Role type never exposes the
 * internal Permission type.
 */
@Entity
@Table(name = "role_permission")
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @MapsId("permissionCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_code")
    private Permission permission;

    protected RolePermission() {
    }

    public RolePermission(Role role, Permission permission) {
        this.role = Objects.requireNonNull(role, "role");
        this.permission = Objects.requireNonNull(permission, "permission");
        // roleId is null for an unsaved role; @MapsId fills it in on persist.
        this.id = new RolePermissionId(role.getId(), permission.getCode());
    }

    public RolePermissionId getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public Permission getPermission() {
        return permission;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RolePermission other)) {
            return false;
        }
        return id != null && id.getRoleId() != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: roleId is only known once the role is persisted.
        return RolePermission.class.hashCode();
    }

    @Override
    public String toString() {
        return "RolePermission[id=" + id + "]";
    }
}
