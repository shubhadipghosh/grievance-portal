package com.shubha.grievanceportal.organization;

import java.util.Set;
import java.util.UUID;

/**
 * Which departments and wards a user may list. Implemented by the identity
 * module, which owns users and grants. Declared here because identity already
 * depends on organization (a grant is scoped to a department and ward), so
 * organization calling into identity would be a module cycle.
 */
public interface ReferenceDataAccess {

    Visible departmentsVisibleTo(UUID userId);

    Visible wardsVisibleTo(UUID userId);

    /** Every active entry, or only those with the listed ids. */
    record Visible(boolean all, Set<UUID> ids) {

        public static final Visible ALL = new Visible(true, Set.of());

        public Visible {
            ids = Set.copyOf(ids);
        }
    }
}
