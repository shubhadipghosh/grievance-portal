package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit row for one re-route (V6, STATE_MACHINE.md "Triage").
 * Read-only here: triage, which writes these, is not built yet. Ward is
 * immutable under triage, so only category and department are mapped.
 */
@Entity
@Table(name = "grievance_reroute")
public class GrievanceReroute {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grievance_id", nullable = false, insertable = false, updatable = false)
    private Grievance grievance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, insertable = false, updatable = false)
    private AppUser actor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "old_category_id", nullable = false, insertable = false, updatable = false)
    private Category oldCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_category_id", nullable = false, insertable = false, updatable = false)
    private Category newCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "old_department_id", nullable = false, insertable = false, updatable = false)
    private Department oldDepartment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_department_id", nullable = false, insertable = false, updatable = false)
    private Department newDepartment;

    @Column(name = "reason", nullable = false, insertable = false, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected GrievanceReroute() {
    }

    public UUID getId() {
        return id;
    }

    public AppUser getActor() {
        return actor;
    }

    public Category getOldCategory() {
        return oldCategory;
    }

    public Category getNewCategory() {
        return newCategory;
    }

    public Department getOldDepartment() {
        return oldDepartment;
    }

    public Department getNewDepartment() {
        return newDepartment;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrievanceReroute other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return GrievanceReroute.class.hashCode();
    }

    @Override
    public String toString() {
        return "GrievanceReroute[id=" + id + "]";
    }
}
