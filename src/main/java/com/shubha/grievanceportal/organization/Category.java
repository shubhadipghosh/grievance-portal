package com.shubha.grievanceportal.organization;

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
 * Two-level taxonomy: {@code parent == null} is a top-level category, otherwise a
 * sub-category. Display names live in {@code category_translation}, never here.
 */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_department_id", nullable = false)
    private Department defaultDepartment;

    @Column(name = "sla_hours", nullable = false)
    private int slaHours;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // DB default now(); null on a freshly persisted instance until reloaded.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Category() {
    }

    public Category(String code, Category parent, Department defaultDepartment, int slaHours) {
        this.code = Objects.requireNonNull(code, "code");
        this.parent = parent;
        this.defaultDepartment = Objects.requireNonNull(defaultDepartment, "defaultDepartment");
        setSlaHours(slaHours);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Category getParent() {
        return parent;
    }

    public void setParent(Category parent) {
        this.parent = parent;
    }

    public Department getDefaultDepartment() {
        return defaultDepartment;
    }

    public void setDefaultDepartment(Department defaultDepartment) {
        this.defaultDepartment = Objects.requireNonNull(defaultDepartment, "defaultDepartment");
    }

    public int getSlaHours() {
        return slaHours;
    }

    public void setSlaHours(int slaHours) {
        if (slaHours <= 0) {
            throw new IllegalArgumentException("slaHours must be positive: " + slaHours);
        }
        this.slaHours = slaHours;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Category other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: stable across persist and safe for Hibernate proxies.
        return Category.class.hashCode();
    }

    @Override
    public String toString() {
        return "Category[id=" + id + ", code=" + code + "]";
    }
}
