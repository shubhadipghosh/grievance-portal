package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.UserStatus;
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
 * Append-only audit row for one user status change. Immutable: constructor
 * only, no setters, and every column is {@code updatable = false} so Hibernate
 * never issues an UPDATE. The DB trigger trg_user_status_immutable is the backstop.
 *
 * <p>Fields are not {@code final}: Hibernate populates entities by reflection,
 * and writing to final fields that way is being phased out (JEP 500).
 */
@Entity
@Table(name = "user_status_history")
public class UserStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    // The column allows NULL for a creation row; a status change always has a from.
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private UserStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private UserStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private AppUser actor;

    @Column(name = "reason", nullable = false, length = 1000, updatable = false)
    private String reason;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected UserStatusHistory() {
    }

    public UserStatusHistory(AppUser user, UserStatus fromStatus, UserStatus toStatus, AppUser actor, String reason) {
        this.user = Objects.requireNonNull(user, "user");
        this.fromStatus = Objects.requireNonNull(fromStatus, "fromStatus");
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public UserStatus getFromStatus() {
        return fromStatus;
    }

    public UserStatus getToStatus() {
        return toStatus;
    }

    public AppUser getActor() {
        return actor;
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
        if (!(o instanceof UserStatusHistory other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return UserStatusHistory.class.hashCode();
    }

    @Override
    public String toString() {
        return "UserStatusHistory[id=" + id + ", " + fromStatus + "->" + toStatus + "]";
    }
}
