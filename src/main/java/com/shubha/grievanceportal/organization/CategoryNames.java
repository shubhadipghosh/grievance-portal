package com.shubha.grievanceportal.organization;

import java.util.UUID;

/** A category's display name. Names live in category_translation, never in code. */
public interface CategoryNames {

    /**
     * The category's name in the caller's most preferred Accept-Language
     * language that it has a translation for, else English; null if it has
     * neither. Inactive categories are named too: old grievances still show them.
     *
     * @param acceptLanguage the raw header, nullable
     */
    String nameOf(UUID categoryId, String acceptLanguage);
}
