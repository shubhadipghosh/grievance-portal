package com.shubha.grievanceportal.organization;

import java.util.UUID;

/**
 * Dropdown entry: enough to pick a category. Name is in the caller's language,
 * or English when there is no translation for it.
 *
 * @param parentId null for a top-level category
 */
public record CategorySummary(UUID id, UUID parentId, String name) {
}
