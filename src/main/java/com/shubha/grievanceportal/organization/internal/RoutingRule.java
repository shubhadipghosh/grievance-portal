package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Routing override: (category, ward) -> department. With no matching rule, the
 * category's default department applies.
 */
@Entity
@Table(name = "routing_rule")
public class RoutingRule {

    @EmbeddedId
    private RoutingRuleId id;

    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @MapsId("wardId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ward_id")
    private Ward ward;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    protected RoutingRule() {
    }

    public RoutingRule(Category category, Ward ward, Department department) {
        this.category = Objects.requireNonNull(category, "category");
        this.ward = Objects.requireNonNull(ward, "ward");
        // Key parts are null for unsaved category/ward; @MapsId fills them in on persist.
        this.id = new RoutingRuleId(category.getId(), ward.getId());
        this.department = Objects.requireNonNull(department, "department");
    }

    public RoutingRuleId getId() {
        return id;
    }

    public Category getCategory() {
        return category;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoutingRule other)) {
            return false;
        }
        return id != null && id.getCategoryId() != null && id.getWardId() != null
                && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: key parts are only known once category and ward are persisted.
        return RoutingRule.class.hashCode();
    }

    @Override
    public String toString() {
        return "RoutingRule[id=" + id + "]";
    }
}
