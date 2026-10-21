package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.shared.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The canView gate (STATE_MACHINE.md, "Errors: precise in the service, gated
 * at the API"). Every grievance endpoint passes through it before anything
 * else. A grievance the actor cannot view fails exactly like one that does not
 * exist, so probing ids or ticket numbers reveals nothing.
 */
@Component
@Transactional(readOnly = true)
class GrievanceViewGate {

    private final GrievanceRepository grievances;
    private final GrievanceAuthorization authorization;

    GrievanceViewGate(GrievanceRepository grievances, GrievanceAuthorization authorization) {
        this.grievances = grievances;
        this.authorization = authorization;
    }

    /** @throws NotFoundException no such grievance, or the actor cannot view it */
    void requireVisible(UUID actorId, UUID grievanceId) {
        grievances.findById(grievanceId)
                .filter(g -> authorization.canView(actorId, g))
                .orElseThrow(() -> new NotFoundException("Grievance", grievanceId));
    }

    /**
     * Whether the actor sees the grievance's internal detail (staff names,
     * internal reasons). Call after {@link #requireVisible}.
     */
    boolean seesInternal(UUID actorId, UUID grievanceId) {
        return grievances.findById(grievanceId)
                .filter(g -> authorization.canViewInternal(actorId, g))
                .isPresent();
    }

    /** @throws NotFoundException no such ticket, or the actor cannot view it */
    void requireVisibleByTicketNo(UUID actorId, String ticketNo) {
        grievances.findByTicketNo(ticketNo)
                .filter(g -> authorization.canView(actorId, g))
                .orElseThrow(() -> new NotFoundException("Grievance", ticketNo));
    }
}
