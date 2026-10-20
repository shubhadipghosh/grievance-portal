package com.shubha.grievanceportal.grievance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Filing and reading grievances. Status changes go through {@link GrievanceTransitionService}. */
public interface GrievanceService {

    /**
     * Files a grievance in SUBMITTED, with a ticket number like GRV-2026-000123.
     *
     * @throws com.shubha.grievanceportal.shared.NotAuthorizedException    filer is not an active citizen
     * @throws com.shubha.grievanceportal.shared.InvalidReferenceException unknown or inactive category or ward
     */
    GrievanceDetails file(UUID citizenId, NewGrievance grievance);

    /** @throws com.shubha.grievanceportal.shared.NotFoundException no such grievance */
    GrievanceDetails get(UUID grievanceId);

    /** @throws com.shubha.grievanceportal.shared.NotFoundException no such grievance */
    GrievanceDetails getByTicketNo(String ticketNo);

    /**
     * Open grievances in one department and status that the actor can view,
     * most urgent (earliest SLA deadline) first. Any sort on the pageable is
     * ignored. Filtered in the query, so pages and totals count only what the
     * actor can see.
     */
    Page<GrievanceSummary> queue(UUID actorId, UUID departmentId, GrievanceStatus status, Pageable pageable);
}
