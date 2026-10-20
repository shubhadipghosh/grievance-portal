package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.GrievanceTransitionService;
import com.shubha.grievanceportal.grievance.HoldOwnerType;
import com.shubha.grievanceportal.grievance.HoldReasonType;
import com.shubha.grievanceportal.grievance.internal.GrievanceTransitions.Transition;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.AuthorizationQuery;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.shared.IllegalTransitionException;
import com.shubha.grievanceportal.shared.IneligibleAssigneeException;
import com.shubha.grievanceportal.shared.InvalidHoldDetailsException;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import com.shubha.grievanceportal.shared.ReasonRequiredException;
import com.shubha.grievanceportal.shared.ReopenLimitReachedException;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
class GrievanceTransitionServiceImpl implements GrievanceTransitionService {

    private final GrievanceRepository grievances;
    private final GrievanceStatusHistoryRepository history;
    private final GrievanceAssignmentRepository assignments;
    private final GrievanceAuthorization authorization;
    private final AuthorizationQuery authorizationQuery;
    private final EntityManager entityManager;
    private final double reopenSlaFactor;

    GrievanceTransitionServiceImpl(GrievanceRepository grievances, GrievanceStatusHistoryRepository history,
                                   GrievanceAssignmentRepository assignments, GrievanceAuthorization authorization,
                                   AuthorizationQuery authorizationQuery, EntityManager entityManager,
                                   @Value("${grievance.sla.reopen-factor:0.5}") double reopenSlaFactor) {
        // A reopen means the first resolution failed: the window may shrink, never grow.
        // Written as a negated range so NaN is rejected too.
        if (!(reopenSlaFactor > 0 && reopenSlaFactor <= 1.0)) {
            throw new IllegalArgumentException(
                    "grievance.sla.reopen-factor must satisfy 0 < factor <= 1.0: " + reopenSlaFactor);
        }
        this.grievances = grievances;
        this.history = history;
        this.assignments = assignments;
        this.authorization = authorization;
        this.authorizationQuery = authorizationQuery;
        this.entityManager = entityManager;
        this.reopenSlaFactor = reopenSlaFactor;
    }

    @Override
    public void transition(UUID grievanceId, GrievanceStatus target, UUID actorId, String reason) {
        Objects.requireNonNull(target, "target");
        if (target == GrievanceStatus.ON_HOLD || target == GrievanceStatus.ASSIGNED) {
            throw new IllegalArgumentException(target + " needs extra input; use its dedicated method");
        }

        Grievance grievance = loadAndCheck(grievanceId, target, actorId, reason);
        GrievanceStatus from = grievance.getStatus();

        // 6. Side effects
        OffsetDateTime now = OffsetDateTime.now();
        if (target == GrievanceStatus.REOPENED) {
            grievance.applyReopen(reopenSlaFactor, now);
        } else {
            grievance.applyTransition(target, now);
        }

        recordAndSave(grievance, from, target, actorId, reason);
    }

    @Override
    public void assign(UUID grievanceId, UUID assigneeId, UUID actorId, String reason) {
        Objects.requireNonNull(assigneeId, "assigneeId");

        Grievance grievance = loadAndCheck(grievanceId, GrievanceStatus.ASSIGNED, actorId, reason);
        GrievanceStatus from = grievance.getStatus();

        // Checked only after the actor is authorised, so an unauthorised caller
        // learns nothing about the assignee.
        AppUser assignee = eligibleAssignee(assigneeId, grievance);

        // 6. Side effects. End any assignment still open from an earlier cycle
        // (REOPENED -> ASSIGNED) so exactly one row matches current_assignee_id.
        OffsetDateTime now = OffsetDateTime.now();
        assignments.findByGrievanceIdOrderByAssignedAt(grievanceId).stream()
                .filter(GrievanceAssignment::isCurrent)
                .forEach(open -> open.end(now));
        grievance.applyAssignment(assignee, now);
        AppUser actor = entityManager.getReference(AppUser.class, actorId);
        assignments.save(new GrievanceAssignment(grievance, assignee, actor, reason));

        recordAndSave(grievance, from, GrievanceStatus.ASSIGNED, actorId, reason);
    }

    @Override
    public void placeOnHold(UUID grievanceId, HoldReasonType reasonType, HoldOwnerType ownerType,
                            UUID holdOwnerId, String holdOwnerNote, UUID actorId, String reason) {
        Objects.requireNonNull(reasonType, "reasonType");
        Objects.requireNonNull(ownerType, "ownerType");

        Grievance grievance = loadAndCheck(grievanceId, GrievanceStatus.ON_HOLD, actorId, reason);
        GrievanceStatus from = grievance.getStatus();

        // Checked only after the actor is authorised, as for assign().
        AppUser holdOwner = holdOwner(grievance, reasonType, ownerType, holdOwnerId, holdOwnerNote);

        // 6. Side effects
        grievance.applyHold(reasonType, ownerType, holdOwner, holdOwnerNote, OffsetDateTime.now());

        recordAndSave(grievance, from, GrievanceStatus.ON_HOLD, actorId, reason);
    }

    /** Steps 1-5. Returns the grievance, still in its original status. */
    private Grievance loadAndCheck(UUID grievanceId, GrievanceStatus target, UUID actorId, String reason) {
        Objects.requireNonNull(grievanceId, "grievanceId");
        Objects.requireNonNull(actorId, "actorId");

        // 1. Load. Concurrent changes are caught by @Version on flush.
        Grievance grievance = grievances.findById(grievanceId)
                .orElseThrow(() -> new NotFoundException("Grievance", grievanceId));
        GrievanceStatus from = grievance.getStatus();

        // 2. Legal?
        Transition transition = GrievanceTransitions.find(from, target)
                .orElseThrow(() -> new IllegalTransitionException(from, target));

        // 3. Authorised?
        if (!authorization.isAuthorized(actorId, grievance, transition.permissions())) {
            throw new NotAuthorizedException(
                    "Actor " + actorId + " may not move grievance " + grievanceId + " " + from + " -> " + target);
        }

        // 4. Reason
        if (transition.reasonRequired() && (reason == null || reason.isBlank())) {
            throw new ReasonRequiredException("A reason is required for " + from + " -> " + target);
        }

        // 5. One reopen per grievance (transitions 13 and 16)
        if (target == GrievanceStatus.REOPENED && grievance.getReopenCount() > 0) {
            throw new ReopenLimitReachedException(grievanceId);
        }
        return grievance;
    }

    private AppUser eligibleAssignee(UUID assigneeId, Grievance grievance) {
        AppUser assignee = entityManager.find(AppUser.class, assigneeId);
        if (assignee == null) {
            throw new IneligibleAssigneeException(assigneeId, "no such user");
        }
        if (assignee.getUserType() != UserType.STAFF) {
            throw new IneligibleAssigneeException(assigneeId, "not staff");
        }
        if (assignee.getStatus() != UserStatus.ACTIVE) {
            throw new IneligibleAssigneeException(assigneeId, "not active");
        }
        if (!authorizationQuery.hasPermissionInScope(assigneeId, Set.of("GRIEVANCE_WORK"),
                grievance.getDepartment().getId(), grievance.getWard().getId())) {
            throw new IneligibleAssigneeException(assigneeId, "no GRIEVANCE_WORK in this grievance's scope");
        }
        return assignee;
    }

    /** Validates the owner details and resolves the owning user; null for EXTERNAL. */
    private AppUser holdOwner(Grievance grievance, HoldReasonType reasonType, HoldOwnerType ownerType,
                              UUID holdOwnerId, String holdOwnerNote) {
        // reasonType alone decides whether the clock pauses, so a citizen-wait must
        // actually be owned by the citizen.
        if (reasonType == HoldReasonType.AWAITING_CITIZEN && ownerType != HoldOwnerType.CITIZEN) {
            throw new InvalidHoldDetailsException("AWAITING_CITIZEN requires owner type CITIZEN, got " + ownerType);
        }
        return switch (ownerType) {
            case CITIZEN -> {
                if (holdOwnerId != null || holdOwnerNote != null) {
                    throw new InvalidHoldDetailsException(
                            "CITIZEN hold owner is the grievance's citizen; holdOwnerId and holdOwnerNote must be absent");
                }
                yield grievance.getCitizen();
            }
            case STAFF -> {
                if (holdOwnerId == null) {
                    throw new InvalidHoldDetailsException("STAFF hold requires holdOwnerId");
                }
                AppUser owner = entityManager.find(AppUser.class, holdOwnerId);
                if (owner == null || owner.getUserType() != UserType.STAFF || owner.getStatus() != UserStatus.ACTIVE) {
                    throw new InvalidHoldDetailsException("Hold owner " + holdOwnerId + " is not an active staff user");
                }
                yield owner;
            }
            case EXTERNAL -> {
                if (holdOwnerNote == null || holdOwnerNote.isBlank()) {
                    throw new InvalidHoldDetailsException("EXTERNAL hold requires holdOwnerNote naming the party");
                }
                if (holdOwnerId != null) {
                    throw new InvalidHoldDetailsException("EXTERNAL hold owner is not a user; holdOwnerId must be absent");
                }
                yield null;
            }
        };
    }

    /** Steps 7-8. */
    private void recordAndSave(Grievance grievance, GrievanceStatus from, GrievanceStatus target,
                               UUID actorId, String reason) {
        // 7. Audit row, unconditionally. Authorization has confirmed the actor exists.
        AppUser actor = entityManager.getReference(AppUser.class, actorId);
        history.save(new GrievanceStatusHistory(grievance, from, target, actor, reason));

        // 8. Save
        grievances.save(grievance);
    }
}
