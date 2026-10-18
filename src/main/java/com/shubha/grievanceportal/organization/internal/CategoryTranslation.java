package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.Category;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "category_translation")
public class CategoryTranslation {

    @EmbeddedId
    private CategoryTranslationId id;

    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    protected CategoryTranslation() {
    }

    public CategoryTranslation(Category category, String locale, String name, String description) {
        this.category = Objects.requireNonNull(category, "category");
        // categoryId is null for an unsaved category; @MapsId fills it in on persist.
        this.id = new CategoryTranslationId(category.getId(), locale);
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
    }

    public CategoryTranslationId getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getLocale() {
        return id.getLocale();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
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
        if (!(o instanceof CategoryTranslation other)) {
            return false;
        }
        return id != null && id.getCategoryId() != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        // Constant per type: categoryId is only known once the category is persisted.
        return CategoryTranslation.class.hashCode();
    }

    @Override
    public String toString() {
        return "CategoryTranslation[id=" + id + "]";
    }
}
