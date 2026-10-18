package com.shubha.grievanceportal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * A named bundle of permissions (SUPER_ADMIN, DEPT_ADMIN, FIELD_OFFICER ...).
 * Its permissions are mapped by the internal RolePermission entity so that
 * this public type does not expose internal ones.
 */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    @Column(name = "description")
    private String description;

    protected Role() {
    }

    public Role(String code, String description) {
        this.code = Objects.requireNonNull(code, "code");
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return Role.class.hashCode();
    }

    @Override
    public String toString() {
        return "Role[id=" + id + ", code=" + code + "]";
    }
}
