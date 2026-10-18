package com.shubha.grievanceportal.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "permission")
public class Permission {

    @Id
    @Column(name = "code", length = 80, updatable = false)
    private String code;

    @Column(name = "description")
    private String description;

    protected Permission() {
    }

    public Permission(String code, String description) {
        this.code = Objects.requireNonNull(code, "code");
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Permission other)) {
            return false;
        }
        return code != null && code.equals(other.getCode());
    }

    @Override
    public int hashCode() {
        // Constant per type, matching the other entities.
        return Permission.class.hashCode();
    }

    @Override
    public String toString() {
        return "Permission[" + code + "]";
    }
}
