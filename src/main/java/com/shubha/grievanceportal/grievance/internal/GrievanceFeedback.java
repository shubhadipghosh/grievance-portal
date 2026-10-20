package com.shubha.grievanceportal.grievance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Citizen's rating of a resolved grievance. Shares the grievance's primary key. */
@Entity
@Table(name = "grievance_feedback")
public class GrievanceFeedback {

    @Id
    @Column(name = "grievance_id")
    private UUID grievanceId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grievance_id")
    private Grievance grievance;

    @Column(name = "rating", nullable = false)
    private short rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected GrievanceFeedback() {
    }

    /**
     * @param rating  1 to 5
     * @param comment nullable
     */
    public GrievanceFeedback(Grievance grievance, int rating, String comment) {
        // grievanceId stays null so Spring Data treats this as new (persist, not
        // merge); @MapsId copies it from the grievance on persist.
        this.grievance = Objects.requireNonNull(grievance, "grievance");
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be 1-5: " + rating);
        }
        this.rating = (short) rating;
        this.comment = comment;
    }

    public UUID getGrievanceId() {
        return grievanceId;
    }

    public Grievance getGrievance() {
        return grievance;
    }

    public short getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrievanceFeedback other)) {
            return false;
        }
        return grievanceId != null && grievanceId.equals(other.getGrievanceId());
    }

    @Override
    public int hashCode() {
        // Constant per type, matching the other entities.
        return GrievanceFeedback.class.hashCode();
    }

    @Override
    public String toString() {
        return "GrievanceFeedback[grievanceId=" + grievanceId + ", rating=" + rating + "]";
    }
}
