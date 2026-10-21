package com.shubha.grievanceportal.grievance;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A row in the citizen's own list. Deliberately not {@link GrievanceSummary}:
 * that carries the assignee, and citizens never see staff names.
 *
 * @param resolvedAt null unless resolved; cleared on reopen and on revert
 */
public record CitizenGrievanceSummary(
        UUID id,
        String ticketNo,
        GrievanceStatus status,
        String title,
        String wardName,
        OffsetDateTime submittedAt,
        OffsetDateTime resolvedAt) {
}
