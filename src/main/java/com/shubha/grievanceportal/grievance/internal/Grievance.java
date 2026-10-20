package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.HoldOwnerType;
import com.shubha.grievanceportal.grievance.HoldReasonType;
import com.shubha.grievanceportal.grievance.Priority;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A citizen's grievance. Children (assignments, status history, comments,
 * feedback) point here; there are deliberately no collections on this side.
 *
 * <p>Fields driven by the status machine (status, assignee, SLA clock,
 * reopen count, hold tracking, nudge scheduling, resolved/closed timestamps)
 * have no setters. They change
 * only through the package-private {@link #applyTransition}.
 */
@Entity
@Table(name = "grievance")
public class Grievance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "ticket_no", nullable = false, unique = true, length = 30, updatable = false)
    private String ticketNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private AppUser citizen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ward_id", nullable = false)
    private Ward ward;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_assignee_id")
    private AppUser currentAssignee;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "address_text", length = 500)
    private String addressText;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GrievanceStatus status = GrievanceStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private Priority priority = Priority.NORMAL;

    @Column(name = "sla_due_at", nullable = false)
    private OffsetDateTime slaDueAt;

    // Set while ON_HOLD.
    @Column(name = "sla_paused_at")
    private OffsetDateTime slaPausedAt;

    // Accumulated across all holds, for reporting.
    @Column(name = "total_paused_seconds", nullable = false)
    private long totalPausedSeconds;

    @Column(name = "reopen_count", nullable = false)
    private short reopenCount;

    // Hold fields: set together while ON_HOLD, all null otherwise (hold_fields_consistent).
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason_type", length = 30)
    private HoldReasonType holdReasonType;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_owner_type", length = 20)
    private HoldOwnerType holdOwnerType;

    // Set when the hold owner is a user; null for external parties.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_owner_id")
    private AppUser holdOwner;

    // External party name; no FK.
    @Column(name = "hold_owner_note", length = 255)
    private String holdOwnerNote;

    @Column(name = "hold_since")
    private OffsetDateTime holdSince;

    // Next 7-day status update while ON_HOLD.
    @Column(name = "next_review_at")
    private OffsetDateTime nextReviewAt;

    // Citizen nudge cycle while RESOLVED or REJECTED.
    @Column(name = "nudge_count", nullable = false)
    private short nudgeCount;

    @Column(name = "next_nudge_at")
    private OffsetDateTime nextNudgeAt;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "submitted_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // DB default on insert; maintained by onUpdate() afterwards (no DB trigger).
    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    protected Grievance() {
    }

    /**
     * Creates a new grievance in {@link GrievanceStatus#SUBMITTED}.
     *
     * @param addressText nullable
     * @param latitude    nullable
     * @param longitude   nullable
     */
    public Grievance(String ticketNo, AppUser citizen, Category category, Ward ward, Department department,
                     String title, String description, String addressText,
                     BigDecimal latitude, BigDecimal longitude, OffsetDateTime slaDueAt) {
        this.ticketNo = Objects.requireNonNull(ticketNo, "ticketNo");
        this.citizen = Objects.requireNonNull(citizen, "citizen");
        this.category = Objects.requireNonNull(category, "category");
        this.ward = Objects.requireNonNull(ward, "ward");
        this.department = Objects.requireNonNull(department, "department");
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.addressText = addressText;
        this.latitude = latitude;
        this.longitude = longitude;
        this.slaDueAt = Objects.requireNonNull(slaDueAt, "slaDueAt");
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Sets the status and its side effects (STATE_MACHINE.md, "Side effects by
     * target state") together. Legality, authorization and reason checks are the
     * caller's job: only the transition service calls this.
     */
    void applyTransition(GrievanceStatus target, OffsetDateTime now) {
        Objects.requireNonNull(target, "target");
        if (target == GrievanceStatus.ASSIGNED) {
            throw new IllegalArgumentException("ASSIGNED needs an assignee; use applyAssignment");
        }
        if (target == GrievanceStatus.ON_HOLD) {
            throw new IllegalArgumentException("ON_HOLD needs hold details; use applyHold");
        }
        if (target == GrievanceStatus.REOPENED) {
            throw new IllegalArgumentException("REOPENED needs the reopen SLA factor; use applyReopen");
        }
        moveTo(target, now);
    }

    /**
     * REOPENED (transitions 13, 16): a fresh, shortened SLA window starting now.
     * total_paused_seconds is history and is not carried into the new window.
     *
     * @param reopenSlaFactor fraction of the category's SLA the reopened cycle gets
     */
    void applyReopen(double reopenSlaFactor, OffsetDateTime now) {
        Objects.requireNonNull(now, "now");
        long windowSeconds = Math.round(category.getSlaHours() * 3600L * reopenSlaFactor);
        slaDueAt = now.plusSeconds(windowSeconds);
        moveTo(GrievanceStatus.REOPENED, now);
    }

    /** ASSIGNED (transitions 3, 17): sets the assignee and status together. */
    void applyAssignment(AppUser assignee, OffsetDateTime now) {
        currentAssignee = Objects.requireNonNull(assignee, "assignee");
        moveTo(GrievanceStatus.ASSIGNED, now);
    }

    /**
     * ON_HOLD (transition 7). Owner validation is the caller's job; this sets the
     * fields hold_fields_consistent requires, together with the status.
     *
     * @param holdOwner     null for EXTERNAL
     * @param holdOwnerNote nullable
     */
    void applyHold(HoldReasonType reasonType, HoldOwnerType ownerType, AppUser holdOwner,
                   String holdOwnerNote, OffsetDateTime now) {
        holdReasonType = Objects.requireNonNull(reasonType, "reasonType");
        holdOwnerType = Objects.requireNonNull(ownerType, "ownerType");
        this.holdOwner = holdOwner;
        this.holdOwnerNote = holdOwnerNote;
        holdSince = Objects.requireNonNull(now, "now");
        nextReviewAt = now.plusDays(7);
        if (reasonType == HoldReasonType.AWAITING_CITIZEN) {
            slaPausedAt = now;
        }
        moveTo(GrievanceStatus.ON_HOLD, now);
    }

    private void moveTo(GrievanceStatus target, OffsetDateTime now) {
        Objects.requireNonNull(now, "now");
        if (status == GrievanceStatus.ON_HOLD) {
            // Any exit from ON_HOLD (transitions 10 and 11). If the hold paused the
            // clock, credit the pause before the target's own side effects run.
            creditPause(now);
            // hold_fields_consistent requires these null outside ON_HOLD.
            holdReasonType = null;
            holdOwnerType = null;
            holdOwner = null;
            holdOwnerNote = null;
            holdSince = null;
            nextReviewAt = null;
        }
        switch (target) {
            case RESOLVED -> {
                resolvedAt = now;
                slaPausedAt = now;
                startNudgeCycle(now);
            }
            case REJECTED -> {
                slaPausedAt = now;
                startNudgeCycle(now);
            }
            case CLOSED -> {
                closedAt = now;
                nextNudgeAt = null;
            }
            case REOPENED -> {
                reopenCount = 1;
                resolvedAt = null;
                // Fresh clock (set by applyReopen), so the stop is not credited.
                slaPausedAt = null;
                nextNudgeAt = null;
            }
            case IN_PROGRESS -> {
                if (status == GrievanceStatus.RESOLVED) {
                    // Staff revert (transition 14): undo the resolution. RESOLVED
                    // stopped the clock, so the time spent there is credited.
                    resolvedAt = null;
                    creditPause(now);
                    nudgeCount = 0;
                    nextNudgeAt = null;
                }
            }
            case ACKNOWLEDGED, ASSIGNED, ON_HOLD -> {
            }
            case SUBMITTED ->
                    throw new IllegalArgumentException("applyTransition does not handle " + target);
        }
        status = target;
    }

    /**
     * Ends a clock stop: adds the stopped time to totalPausedSeconds and extends
     * slaDueAt by the same amount. Whole seconds for both, so they agree exactly.
     */
    private void creditPause(OffsetDateTime now) {
        if (slaPausedAt == null) {
            return;
        }
        long paused = Duration.between(slaPausedAt, now).getSeconds();
        totalPausedSeconds += paused;
        slaDueAt = slaDueAt.plusSeconds(paused);
        slaPausedAt = null;
    }

    private void startNudgeCycle(OffsetDateTime now) {
        nudgeCount = 0;
        nextNudgeAt = now.plusDays(5);
    }

    public UUID getId() {
        return id;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public AppUser getCitizen() {
        return citizen;
    }

    public Category getCategory() {
        return category;
    }

    // Not public: category drives the SLA. Changed only by triage (not yet built).
    void setCategory(Category category) {
        this.category = Objects.requireNonNull(category, "category");
    }

    public Ward getWard() {
        return ward;
    }

    public Department getDepartment() {
        return department;
    }

    // Not public: department is half the authorization scope. Changed only by
    // triage (not yet built), which must check GRIEVANCE_TRIAGE on the original
    // department first.
    void setDepartment(Department department) {
        this.department = Objects.requireNonNull(department, "department");
    }

    public AppUser getCurrentAssignee() {
        return currentAssignee;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAddressText() {
        return addressText;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public GrievanceStatus getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = Objects.requireNonNull(priority, "priority");
    }

    public OffsetDateTime getSlaDueAt() {
        return slaDueAt;
    }

    public OffsetDateTime getSlaPausedAt() {
        return slaPausedAt;
    }

    public long getTotalPausedSeconds() {
        return totalPausedSeconds;
    }

    public short getReopenCount() {
        return reopenCount;
    }

    public HoldReasonType getHoldReasonType() {
        return holdReasonType;
    }

    public HoldOwnerType getHoldOwnerType() {
        return holdOwnerType;
    }

    public AppUser getHoldOwner() {
        return holdOwner;
    }

    public String getHoldOwnerNote() {
        return holdOwnerNote;
    }

    public OffsetDateTime getHoldSince() {
        return holdSince;
    }

    public OffsetDateTime getNextReviewAt() {
        return nextReviewAt;
    }

    public short getNudgeCount() {
        return nudgeCount;
    }

    public OffsetDateTime getNextNudgeAt() {
        return nextNudgeAt;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Grievance other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return Grievance.class.hashCode();
    }

    @Override
    public String toString() {
        return "Grievance[id=" + id + ", ticketNo=" + ticketNo + ", status=" + status + "]";
    }
}
