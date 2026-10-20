package com.shubha.grievanceportal.grievance;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Queue row: just enough to triage a list on a slow connection. */
public record GrievanceSummary(
        UUID id,
        String ticketNo,
        GrievanceStatus status,
        Priority priority,
        UUID departmentId,
        UUID wardId,
        String title,
        UUID currentAssigneeId,
        OffsetDateTime slaDueAt) {
}
