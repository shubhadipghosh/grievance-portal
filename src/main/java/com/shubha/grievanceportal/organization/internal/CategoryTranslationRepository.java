package com.shubha.grievanceportal.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, CategoryTranslationId> {

    List<CategoryTranslation> findByIdCategoryId(UUID categoryId);
}
