package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceDetails;
import com.shubha.grievanceportal.grievance.GrievanceService;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.GrievanceSummary;
import com.shubha.grievanceportal.grievance.GrievanceTransitionService;
import com.shubha.grievanceportal.grievance.HoldOwnerType;
import com.shubha.grievanceportal.grievance.HoldReasonType;
import com.shubha.grievanceportal.grievance.NewGrievance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    /**
     * TODO(step 4, TEMPORARY): the actor is read from this header, so any
     * caller can claim to be anyone. Replace every use with the authenticated
     * principal when login is built. Only active under the "stub-auth"
     * profile (identity.internal.StubAuthSecurityConfig); without it the API
     * is locked.
     */
    static final String ACTOR_HEADER = "X-Actor-Id";

    private final GrievanceService grievances;
    private final GrievanceTransitionService transitions;

    GrievanceController(GrievanceService grievances, GrievanceTransitionService transitions) {
        this.grievances = grievances;
        this.transitions = transitions;
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
    ResponseEntity<GrievanceDetails> file(@RequestHeader(ACTOR_HEADER) UUID actorId,
                                          @Valid @RequestBody NewGrievance body) {
        GrievanceDetails created = grievances.file(actorId, body);
        return ResponseEntity.created(URI.create("/api/v1/grievances/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    GrievanceDetails get(@RequestHeader(ACTOR_HEADER) UUID actorId, @PathVariable UUID id) {
        // TODO(canView): gate first; not visible -> 404, same as a missing grievance.
        return grievances.get(id);
    }

    /** Citizens look up by ticket number; everything else addresses by id. */
    @GetMapping(params = "ticketNo")
    GrievanceDetails getByTicketNo(@RequestHeader(ACTOR_HEADER) UUID actorId, @RequestParam String ticketNo) {
        // TODO(canView): gate first; not visible -> 404, same as a missing ticket.
        return grievances.getByTicketNo(ticketNo);
    }

    @GetMapping
    PagedModel<GrievanceSummary> queue(@RequestHeader(ACTOR_HEADER) UUID actorId,
                                       @RequestParam UUID departmentId,
                                       @RequestParam GrievanceStatus status,
                                       @PageableDefault(size = 20) Pageable pageable) {
        // TODO(canView): restrict to grievances within the actor's granted scope.
        return new PagedModel<>(grievances.queue(departmentId, status, pageable));
    }

    @PostMapping("/{id}/acknowledge")
    GrievanceDetails acknowledge(@RequestHeader(ACTOR_HEADER) UUID actorId, @PathVariable UUID id,
                                 @Valid @RequestBody(required = false) ReasonRequest body) {
        // TODO(canView): gate first; not visible -> 404.
        transitions.transition(id, GrievanceStatus.ACKNOWLEDGED, actorId, reason(body));
        return grievances.get(id);
    }

    @PostMapping("/{id}/assign")
    GrievanceDetails assign(@RequestHeader(ACTOR_HEADER) UUID actorId, @PathVariable UUID id,
                            @Valid @RequestBody AssignRequest body) {
        // TODO(canView): gate first; not visible -> 404.
        transitions.assign(id, body.assigneeId(), actorId, body.reason());
        return grievances.get(id);
    }

    @PostMapping("/{id}/hold")
    GrievanceDetails hold(@RequestHeader(ACTOR_HEADER) UUID actorId, @PathVariable UUID id,
                          @Valid @RequestBody HoldRequest body) {
        // TODO(canView): gate first; not visible -> 404.
        transitions.placeOnHold(id, body.reasonType(), body.ownerType(), body.holdOwnerId(),
                body.holdOwnerNote(), actorId, body.reason());
        return grievances.get(id);
    }

    @PostMapping("/{id}/resolve")
    GrievanceDetails resolve(@RequestHeader(ACTOR_HEADER) UUID actorId, @PathVariable UUID id,
                             @Valid @RequestBody(required = false) ReasonRequest body) {
        // TODO(canView): gate first; not visible -> 404.
        transitions.transition(id, GrievanceStatus.RESOLVED, actorId, reason(body));
        return grievances.get(id);
    }

    /** Transitions 12 (from RESOLVED) and 15 (from REJECTED): the table decides which applies. */
    @PostMapping("/{id}/close")
    GrievanceDetails close(@RequestHeader(ACTOR_HEADER) UUID actorId, @PathVariable UUID id,
                           @Valid @RequestBody(required = false) ReasonRequest body) {
        // TODO(canView): gate first; not visible -> 404.
        transitions.transition(id, GrievanceStatus.CLOSED, actorId, reason(body));
        return grievances.get(id);
    }

    private static String reason(ReasonRequest body) {
        return body == null ? null : body.reason();
    }
}
