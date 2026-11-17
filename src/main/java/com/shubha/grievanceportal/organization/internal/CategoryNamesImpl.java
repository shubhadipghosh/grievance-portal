package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.CategoryNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
class CategoryNamesImpl implements CategoryNames {

    private final CategoryRepository categories;

    CategoryNamesImpl(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    public String nameOf(UUID categoryId, String acceptLanguage) {
        return categories.findName(categoryId, ReferenceDataController.languages(acceptLanguage)).orElse(null);
    }
}
