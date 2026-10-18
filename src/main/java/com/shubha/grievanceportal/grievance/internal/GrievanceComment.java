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

@Entity
@Table(name = "grievance_comment")
public class GrievanceComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grievance_id", nullable = false, updatable = false)
    private Grievance grievance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private AppUser author;

    @Column(name = "body", nullable = false, updatable = false)
    private String body;

    // Hidden from the citizen.
    @Column(name = "internal_only", nullable = false, updatable = false)
    private boolean internalOnly;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected GrievanceComment() {
    }

    public GrievanceComment(Grievance grievance, AppUser author, String body, boolean internalOnly) {
        this.grievance = Objects.requireNonNull(grievance, "grievance");
        this.author = Objects.requireNonNull(author, "author");
        this.body = Objects.requireNonNull(body, "body");
        this.internalOnly = internalOnly;
    }

    public UUID getId() {
        return id;
    }

    public Grievance getGrievance() {
        return grievance;
    }

    public AppUser getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public boolean isInternalOnly() {
        return internalOnly;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GrievanceComment other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return GrievanceComment.class.hashCode();
    }

    @Override
    public String toString() {
        return "GrievanceComment[id=" + id + ", internalOnly=" + internalOnly + "]";
    }
}
