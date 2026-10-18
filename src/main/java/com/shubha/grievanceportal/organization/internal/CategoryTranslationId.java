package com.shubha.grievanceportal.organization.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CategoryTranslationId implements Serializable {

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    protected CategoryTranslationId() {
    }

    public CategoryTranslationId(UUID categoryId, String locale) {
        this.categoryId = categoryId;
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getLocale() {
        return locale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CategoryTranslationId other)) {
            return false;
        }
        return Objects.equals(categoryId, other.categoryId) && Objects.equals(locale, other.locale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId, locale);
    }

    @Override
    public String toString() {
        return categoryId + "/" + locale;
    }
}
