package com.shubha.grievanceportal.grievance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Full view of one grievance. Nullable fields are null when not applicable. */
public record GrievanceDetails(
        UUID id,
        String ticketNo,
        GrievanceStatus status,
        Priority priority,
        UUID categoryId,
        UUID departmentId,
        UUID wardId,
        String title,
        String description,
        String addressText,
        BigDecimal latitude,
        BigDecimal longitude,
        UUID currentAssigneeId,
        OffsetDateTime slaDueAt,
        OffsetDateTime submittedAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime closedAt,
        short reopenCount,
        HoldReasonType holdReasonType,
        HoldOwnerType holdOwnerType,
        OffsetDateTime holdSince) {
}
