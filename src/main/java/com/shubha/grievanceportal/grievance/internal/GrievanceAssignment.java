package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.Grievance;
import com.shubha.grievanceportal.identity.AppUser;
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
 * One assignment period. grievance.current_assignee_id is the denormalized
 * "now"; this table is the full history.
 */
@Entity
@Table(name = "grievance_assignment")
public class GrievanceAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grievance_id", nullable = false, updatable = false)
    private Grievance grievance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id", nullable = false, updatable = false)
    private AppUser assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false, updatable = false)
    private AppUser assignedBy;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "assigned_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "unassigned_at")
    private OffsetDateTime unassignedAt;

    protected GrievanceAssignment() {
    }

    /** @param reason nullable */
    public GrievanceAssignment(Grievance grievance, AppUser assignee, AppUser assignedBy, String reason) {
        this.grievance = Objects.requireNonNull(grievance, "grievance");
        this.assignee = Objects.requireNonNull(assignee, "assignee");
        this.assignedBy = Objects.requireNonNull(assignedBy, "assignedBy");
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public Grievance getGrievance() {
        return grievance;
    }

    public AppUser getAssignee() {
        return assignee;
    }

    public AppUser getAssignedBy() {
        return assignedBy;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public OffsetDateTime getUnassignedAt() {
        return unassignedAt;
    }

    public boolean isCurrent() {
        return unassignedAt == null;
    }

    public void end(OffsetDateTime at) {
        if (unassignedAt != null) {
            throw new IllegalStateException("Assignment " + id + " already ended");
        }
        this.unassignedAt = Objects.requireNonNull(at, "at");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrievanceAssignment other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return GrievanceAssignment.class.hashCode();
    }

    @Override
    public String toString() {
        return "GrievanceAssignment[id=" + id + "]";
    }
}
