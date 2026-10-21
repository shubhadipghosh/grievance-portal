package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.CategorySummary;
import com.shubha.grievanceportal.organization.DepartmentSummary;
import com.shubha.grievanceportal.organization.ReferenceDataAccess;
import com.shubha.grievanceportal.organization.WardSummary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Departments, wards and categories for dropdowns. Departments and wards are
 * limited to what the caller may see (see {@link ReferenceDataAccess});
 * categories are not scoped. Inactive entries are never listed.
 */
@RestController
@RequestMapping("/api/v1")
class ReferenceDataController {

    private final DepartmentRepository departments;
    private final WardRepository wards;
    private final CategoryRepository categories;
    private final ReferenceDataAccess access;

    ReferenceDataController(DepartmentRepository departments, WardRepository wards,
                            CategoryRepository categories, ReferenceDataAccess access) {
        this.departments = departments;
        this.wards = wards;
        this.categories = categories;
        this.access = access;
    }

    @GetMapping("/departments")
    List<DepartmentSummary> departments(Principal principal) {
        ReferenceDataAccess.Visible visible = access.departmentsVisibleTo(userId(principal));
        return visible.all() ? departments.findActiveSummaries() : departments.findActiveSummaries(visible.ids());
    }

    @GetMapping("/wards")
    List<WardSummary> wards(Principal principal) {
        ReferenceDataAccess.Visible visible = access.wardsVisibleTo(userId(principal));
        return visible.all() ? wards.findActiveSummaries() : wards.findActiveSummaries(visible.ids());
    }

    /**
     * Each category named in the caller's most preferred Accept-Language
     * language that it has a translation for, else English.
     */
    @GetMapping("/categories")
    List<CategorySummary> categories(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return categories.findActiveSummaries(languages(acceptLanguage)).stream()
                .map(row -> new CategorySummary(row.getId(), row.getParentId(), row.getName()))
                .toList();
    }

    // Read the header directly: the servlet's request locale falls back to the
    // JVM default when the header is absent, which is not necessarily English.
    // Translations are keyed by bare language ('bn'), so "bn-IN" becomes "bn".
    /** Languages in preference order, always ending with English. */
    static String[] languages(String acceptLanguage) {
        Set<String> languages = new LinkedHashSet<>();
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            try {
                // parse() sorts by quality; q=0 means "not acceptable".
                for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
                    String language = Locale.forLanguageTag(range.getRange()).getLanguage();
                    if (range.getWeight() > 0 && !language.isEmpty()) {
                        languages.add(language);
                    }
                }
            } catch (IllegalArgumentException malformed) {
                languages.clear();
            }
        }
        languages.add("en");
        return languages.toArray(String[]::new);
    }

    // The principal is identity's CurrentUser, whose name is the user id. Read
    // through java.security.Principal so this module does not depend on identity.
    private static UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
