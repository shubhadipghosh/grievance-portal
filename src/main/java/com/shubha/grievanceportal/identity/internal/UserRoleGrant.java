package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.Role;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Scoped role grant. A null department or ward means unrestricted on that axis.
 * Grants are never edited; they are revoked and a new one issued.
 */
@Entity
@Table(name = "user_role_grant")
public class UserRoleGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, updatable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", updatable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id", updatable = false)
    private Ward ward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by", updatable = false)
    private AppUser grantedBy;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "granted_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime grantedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private AppUser revokedBy;

    @Column(name = "revoke_reason", length = 1000)
    private String revokeReason;

    protected UserRoleGrant() {
    }

    /**
     * @param department null = all departments
     * @param ward       null = all wards
     * @param grantedBy  null for system/seed grants
     */
    public UserRoleGrant(AppUser user, Role role, Department department, Ward ward, AppUser grantedBy) {
        this.user = Objects.requireNonNull(user, "user");
        this.role = Objects.requireNonNull(role, "role");
        this.department = department;
        this.ward = ward;
        this.grantedBy = grantedBy;
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public Department getDepartment() {
        return department;
    }

    public Ward getWard() {
        return ward;
    }

    public AppUser getGrantedBy() {
        return grantedBy;
    }

    public OffsetDateTime getGrantedAt() {
        return grantedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public AppUser getRevokedBy() {
        return revokedBy;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    /** Once only; the DB trigger trg_grant_revoke_only refuses any other change. */
    public void revoke(OffsetDateTime at, AppUser by, String reason) {
        if (revokedAt != null) {
            throw new IllegalStateException("Grant " + id + " already revoked");
        }
        this.revokedAt = Objects.requireNonNull(at, "at");
        this.revokedBy = Objects.requireNonNull(by, "by");
        this.revokeReason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRoleGrant other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return UserRoleGrant.class.hashCode();
    }

    @Override
    public String toString() {
        return "UserRoleGrant[id=" + id + "]";
    }
}
