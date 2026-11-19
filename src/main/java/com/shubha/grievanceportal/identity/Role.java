package com.shubha.grievanceportal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // Set by migration, enforced by trg_grant_scope.
    @Enumerated(EnumType.STRING)
    @Column(name = "department_scope", nullable = false, insertable = false, updatable = false)
    private ScopeRule departmentScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "ward_scope", nullable = false, insertable = false, updatable = false)
    private ScopeRule wardScope;

    /** Whether a grant of this role must, must not, or may set an axis. */
    public enum ScopeRule {
        REQUIRED, FORBIDDEN, OPTIONAL;

        public boolean allows(boolean set) {
            return switch (this) {
                case REQUIRED -> set;
                case FORBIDDEN -> !set;
                case OPTIONAL -> true;
            };
        }
    }

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

    public ScopeRule getDepartmentScope() {
        return departmentScope;
    }

    public ScopeRule getWardScope() {
        return wardScope;
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
