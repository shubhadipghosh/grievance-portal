package com.shubha.grievanceportal.organization.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RoutingRuleId implements Serializable {

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "ward_id", nullable = false)
    private UUID wardId;

    protected RoutingRuleId() {
    }

    public RoutingRuleId(UUID categoryId, UUID wardId) {
        this.categoryId = categoryId;
        this.wardId = wardId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getWardId() {
        return wardId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoutingRuleId other)) {
            return false;
        }
        return Objects.equals(categoryId, other.categoryId) && Objects.equals(wardId, other.wardId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId, wardId);
    }

    @Override
    public String toString() {
        return categoryId + "/" + wardId;
    }
}
