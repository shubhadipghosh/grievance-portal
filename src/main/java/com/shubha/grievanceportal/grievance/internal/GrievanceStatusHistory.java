package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.HoldReasonType;
import com.shubha.grievanceportal.identity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Append-only audit row for one status change. Immutable: constructor only,
 * no setters, and every column is {@code updatable = false} so Hibernate never
 * issues an UPDATE. The DB trigger trg_status_history_immutable is the backstop.
 *
 * <p>Fields are not {@code final}: Hibernate populates entities by reflection,
 * and writing to final fields that way is being phased out (JEP 500).
 */
@Entity
@Table(name = "grievance_status_history")
public class GrievanceStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grievance_id", nullable = false, updatable = false)
    private Grievance grievance;

    // Null on creation.
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private GrievanceStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private GrievanceStatus toStatus;

    // Automated changes use the SYSTEM user (SystemActor.ID), never null.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private AppUser actor;

    @Column(name = "reason", length = 1000, updatable = false)
    private String reason;

    // Set only on ON_HOLD rows (from V10 on): the grievance's own copy is cleared when the hold ends.
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason_type", length = 30, updatable = false)
    private HoldReasonType holdReasonType;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected GrievanceStatusHistory() {
    }

    /**
     * @param fromStatus null for the initial SUBMITTED row
     * @param reason     nullable
     */
    public GrievanceStatusHistory(Grievance grievance, GrievanceStatus fromStatus, GrievanceStatus toStatus,
                                  AppUser actor, String reason) {
        this(grievance, fromStatus, toStatus, actor, reason, null);
    }

    /** @param holdReasonType required when toStatus is ON_HOLD, otherwise null */
    public GrievanceStatusHistory(Grievance grievance, GrievanceStatus fromStatus, GrievanceStatus toStatus,
                                  AppUser actor, String reason, HoldReasonType holdReasonType) {
        this.grievance = Objects.requireNonNull(grievance, "grievance");
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.reason = reason;
        if ((toStatus == GrievanceStatus.ON_HOLD) != (holdReasonType != null)) {
            throw new IllegalArgumentException("holdReasonType is required on, and only on, an ON_HOLD row");
        }
        this.holdReasonType = holdReasonType;
    }

    public UUID getId() {
        return id;
    }

    public Grievance getGrievance() {
        return grievance;
    }

    public GrievanceStatus getFromStatus() {
        return fromStatus;
    }

    public GrievanceStatus getToStatus() {
        return toStatus;
    }

    public AppUser getActor() {
        return actor;
    }

    public String getReason() {
        return reason;
    }

    public HoldReasonType getHoldReasonType() {
        return holdReasonType;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrievanceStatusHistory other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return GrievanceStatusHistory.class.hashCode();
    }

    @Override
    public String toString() {
        return "GrievanceStatusHistory[id=" + id + ", " + fromStatus + "->" + toStatus + "]";
    }
}
