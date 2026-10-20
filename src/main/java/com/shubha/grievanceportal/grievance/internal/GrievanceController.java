package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceDetails;
import com.shubha.grievanceportal.grievance.GrievanceService;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.GrievanceSummary;
import com.shubha.grievanceportal.grievance.GrievanceTransitionService;
import com.shubha.grievanceportal.grievance.HoldOwnerType;
import com.shubha.grievanceportal.grievance.HoldReasonType;
import com.shubha.grievanceportal.grievance.NewGrievance;
import com.shubha.grievanceportal.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Grievance endpoints. Status-changing endpoints return the updated grievance
 * so a phone on a slow connection does not need a second request.
 *
 * <p>Error responses come from shared.ApiExceptionHandler as RFC 9457
 * ProblemDetail.
 */
@RestController
@RequestMapping("/api/v1/grievances")
class GrievanceController {

    private final GrievanceService grievances;
    private final GrievanceTransitionService transitions;
    private final GrievanceViewGate viewGate;

    GrievanceController(GrievanceService grievances, GrievanceTransitionService transitions,
                        GrievanceViewGate viewGate) {
        this.grievances = grievances;
        this.transitions = transitions;
        this.viewGate = viewGate;
    }

    record ReasonRequest(@Size(max = 1000) String reason) {
    }

    // grievance_assignment.reason is VARCHAR(500).
    record AssignRequest(@NotNull UUID assigneeId, @Size(max = 500) String reason) {
    }

    record HoldRequest(@NotNull HoldReasonType reasonType, @NotNull HoldOwnerType ownerType,
                       UUID holdOwnerId, @Size(max = 255) String holdOwnerNote,
                       @Size(max = 1000) String reason) {
    }

    @PostMapping
    ResponseEntity<GrievanceDetails> file(@AuthenticationPrincipal CurrentUser actor,
                                          @Valid @RequestBody NewGrievance body) {
        GrievanceDetails created = grievances.file(actor.id(), body);
        return ResponseEntity.created(URI.create("/api/v1/grievances/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    GrievanceDetails get(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id) {
        viewGate.requireVisible(actor.id(), id);
        return grievances.get(id);
    }

    /** Citizens look up by ticket number; everything else addresses by id. */
    @GetMapping(params = "ticketNo")
    GrievanceDetails getByTicketNo(@AuthenticationPrincipal CurrentUser actor, @RequestParam String ticketNo) {
        viewGate.requireVisibleByTicketNo(actor.id(), ticketNo);
        return grievances.getByTicketNo(ticketNo);
    }

    @GetMapping
    PagedModel<GrievanceSummary> queue(@AuthenticationPrincipal CurrentUser actor,
                                       @RequestParam UUID departmentId,
                                       @RequestParam GrievanceStatus status,
                                       @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(grievances.queue(actor.id(), departmentId, status, pageable));
    }

    @PostMapping("/{id}/acknowledge")
    GrievanceDetails acknowledge(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                                 @Valid @RequestBody(required = false) ReasonRequest body) {
        viewGate.requireVisible(actor.id(), id);
        transitions.transition(id, GrievanceStatus.ACKNOWLEDGED, actor.id(), reason(body));
        return grievances.get(id);
    }

    @PostMapping("/{id}/assign")
    GrievanceDetails assign(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                            @Valid @RequestBody AssignRequest body) {
        viewGate.requireVisible(actor.id(), id);
        transitions.assign(id, body.assigneeId(), actor.id(), body.reason());
        return grievances.get(id);
    }

    @PostMapping("/{id}/hold")
    GrievanceDetails hold(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                          @Valid @RequestBody HoldRequest body) {
        viewGate.requireVisible(actor.id(), id);
        transitions.placeOnHold(id, body.reasonType(), body.ownerType(), body.holdOwnerId(),
                body.holdOwnerNote(), actor.id(), body.reason());
        return grievances.get(id);
    }

    @PostMapping("/{id}/resolve")
    GrievanceDetails resolve(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                             @Valid @RequestBody(required = false) ReasonRequest body) {
        viewGate.requireVisible(actor.id(), id);
        transitions.transition(id, GrievanceStatus.RESOLVED, actor.id(), reason(body));
        return grievances.get(id);
    }

    /** Transitions 12 (from RESOLVED) and 15 (from REJECTED): the table decides which applies. */
    @PostMapping("/{id}/close")
    GrievanceDetails close(@AuthenticationPrincipal CurrentUser actor, @PathVariable UUID id,
                           @Valid @RequestBody(required = false) ReasonRequest body) {
        viewGate.requireVisible(actor.id(), id);
        transitions.transition(id, GrievanceStatus.CLOSED, actor.id(), reason(body));
        return grievances.get(id);
    }

    private static String reason(ReasonRequest body) {
        return body == null ? null : body.reason();
    }
}
