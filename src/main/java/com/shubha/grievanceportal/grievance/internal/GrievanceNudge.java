package com.shubha.grievanceportal.grievance.internal;

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
 * Audit row proving the citizen was contacted before an auto-close. Immutable:
 * constructor only, no setters, every column {@code updatable = false}. The DB
 * trigger trg_nudge_immutable is the backstop.
 *
 * <p>Delivery receipts are deliberately not tracked here. They arrive
 * asynchronously from the gateway and are mutable, which an append-only table
 * cannot express. They belong to the notification module.
 *
 * <p>Fields are not {@code final}: Hibernate populates entities by reflection,
 * and writing to final fields that way is being phased out (JEP 500).
 */
@Entity
@Table(name = "grievance_nudge")
public class GrievanceNudge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grievance_id", nullable = false, updatable = false)
    private Grievance grievance;

    @Column(name = "nudge_number", nullable = false, updatable = false)
    private short nudgeNumber;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "sent_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20, updatable = false)
    private NudgeChannel channel;

    protected GrievanceNudge() {
    }

    /**
     * @param nudgeNumber 1 to 3
     */
    public GrievanceNudge(Grievance grievance, int nudgeNumber, NudgeChannel channel) {
        this.grievance = Objects.requireNonNull(grievance, "grievance");
        if (nudgeNumber < 1 || nudgeNumber > 3) {
            throw new IllegalArgumentException("nudgeNumber must be 1-3: " + nudgeNumber);
        }
        this.nudgeNumber = (short) nudgeNumber;
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    public UUID getId() {
        return id;
    }

    public Grievance getGrievance() {
        return grievance;
    }

    public short getNudgeNumber() {
        return nudgeNumber;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public NudgeChannel getChannel() {
        return channel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrievanceNudge other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return GrievanceNudge.class.hashCode();
    }

    @Override
    public String toString() {
        return "GrievanceNudge[id=" + id + ", nudgeNumber=" + nudgeNumber + ", channel=" + channel + "]";
    }
}