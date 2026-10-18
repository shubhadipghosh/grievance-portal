package com.shubha.grievanceportal.grievance;

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
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A citizen's grievance. Children (assignments, status history, comments,
 * feedback) point here; there are deliberately no collections on this side.
 *
 * <p>Fields driven by the status machine (status, assignee, SLA clock,
 * reopen count, resolved/closed timestamps) have no setters. They change
 * only through the transition logic, which is not part of this class yet.
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

    @Column(name = "reopen_count", nullable = false)
    private short reopenCount;

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

    public void setCategory(Category category) {
        this.category = Objects.requireNonNull(category, "category");
    }

    public Ward getWard() {
        return ward;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
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

    public short getReopenCount() {
        return reopenCount;
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
