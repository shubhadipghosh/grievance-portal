package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.GrievanceTransitionService;
import com.shubha.grievanceportal.grievance.internal.GrievanceTransitions.Transition;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.shared.IllegalTransitionException;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import com.shubha.grievanceportal.shared.ReasonRequiredException;
import com.shubha.grievanceportal.shared.ReopenLimitReachedException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
class GrievanceTransitionServiceImpl implements GrievanceTransitionService {

    private final GrievanceRepository grievances;
    private final GrievanceStatusHistoryRepository history;
    private final GrievanceAuthorization authorization;
    private final EntityManager entityManager;

    GrievanceTransitionServiceImpl(GrievanceRepository grievances, GrievanceStatusHistoryRepository history,
                                   GrievanceAuthorization authorization, EntityManager entityManager) {
        this.grievances = grievances;
        this.history = history;
        this.authorization = authorization;
        this.entityManager = entityManager;
    }

    @Override
    public void transition(UUID grievanceId, GrievanceStatus target, UUID actorId, String reason) {
        Objects.requireNonNull(grievanceId, "grievanceId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actorId, "actorId");
        if (target == GrievanceStatus.ON_HOLD || target == GrievanceStatus.ASSIGNED) {
            throw new IllegalArgumentException(target + " needs extra input; use its dedicated method");
        }

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

        // 6. Side effects
        grievance.applyTransition(target, OffsetDateTime.now());

        // 7. Audit row, unconditionally. Authorization has confirmed the actor exists.
        AppUser actor = entityManager.getReference(AppUser.class, actorId);
        history.save(new GrievanceStatusHistory(grievance, from, target, actor, reason));

        // 8. Save
        grievances.save(grievance);
    }
}
