package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceService;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.StaffDirectory;
import com.shubha.grievanceportal.identity.StaffMember;
import com.shubha.grievanceportal.organization.CategoryNames;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.shubha.grievanceportal.grievance.internal.GrievanceTransitions.CITIZEN_OWNER;

/**
 * The ticket page: overview, activity feed, comments. Callers pass the
 * canView gate first; what the viewer sees past it is decided here.
 */
@Service
@Transactional(readOnly = true)
class GrievanceTicketService {

    static final String COMMENT = "GRIEVANCE_COMMENT";
    static final String ASSIGN = "GRIEVANCE_ASSIGN";
    static final String WORK = "GRIEVANCE_WORK";

    private final GrievanceRepository grievances;
    private final GrievanceService grievanceService;
    private final GrievanceCommentRepository comments;
    private final GrievanceAssignmentRepository assignments;
    private final GrievanceRerouteRepository reroutes;
    private final GrievanceAuthorization authorization;
    private final StaffDirectory staffDirectory;
    private final CategoryNames categoryNames;
    private final EntityManager entityManager;

    GrievanceTicketService(GrievanceRepository grievances, GrievanceService grievanceService,
                           GrievanceCommentRepository comments, GrievanceAssignmentRepository assignments,
                           GrievanceRerouteRepository reroutes, GrievanceAuthorization authorization,
                           StaffDirectory staffDirectory, CategoryNames categoryNames,
                           EntityManager entityManager) {
        this.grievances = grievances;
        this.grievanceService = grievanceService;
        this.comments = comments;
        this.assignments = assignments;
        this.reroutes = reroutes;
        this.authorization = authorization;
        this.staffDirectory = staffDirectory;
        this.categoryNames = categoryNames;
        this.entityManager = entityManager;
    }

    TicketOverview overview(UUID grievanceId, UUID viewerId, String acceptLanguage) {
        Grievance g = load(grievanceId);
        UUID departmentId = g.getDepartment().getId();
        UUID wardId = g.getWard().getId();
        AppUser assignee = g.getCurrentAssignee();
        return new TicketOverview(grievanceService.get(grievanceId),
                categoryNames.nameOf(g.getCategory().getId(), acceptLanguage),
                g.getDepartment().getName(),
                g.getWard().getName(),
                g.getCitizen().getFullName(),
                assignee == null ? null : assignee.getFullName(),
                staffDirectory.namesWithRoleInScope("DEPT_ADMIN", departmentId, wardId),
                authorization.isAuthorized(viewerId, g, Set.of(CITIZEN_OWNER, COMMENT)),
                authorization.isAuthorized(viewerId, g, Set.of(COMMENT)),
                actions(g, viewerId));
    }

    /**
     * The transitions out of the current status that the viewer may make, by the
     * same permission and reopen-limit rules the transition service enforces.
     */
    private List<TicketOverview.Action> actions(Grievance g, UUID viewerId) {
        return GrievanceTransitions.from(g.getStatus()).stream()
                .filter(t -> !(t.to() == GrievanceStatus.REOPENED && g.getReopenCount() > 0))
                .filter(t -> authorization.isAuthorized(viewerId, g, t.permissions()))
                .map(t -> new TicketOverview.Action(t.to(), t.reasonRequired()))
                .toList();
    }

    /**
     * Who the grievance can be assigned to: ACTIVE staff with GRIEVANCE_WORK in
     * its scope, the same rule the assign transition checks.
     *
     * @throws NotAuthorizedException the viewer may not assign this grievance
     */
    List<StaffMember> assignees(UUID grievanceId, UUID viewerId) {
        Grievance g = load(grievanceId);
        if (!authorization.isAuthorized(viewerId, g, Set.of(ASSIGN))) {
            throw new NotAuthorizedException("You may not assign this grievance");
        }
        return staffDirectory.staffWithPermissionInScope(WORK, g.getDepartment().getId(), g.getWard().getId());
    }

    /**
     * Everything that happened, oldest first. Events in one transaction share a
     * timestamp; they keep the order status, assignment, reroute, comment.
     */
    List<ActivityItem> activity(UUID grievanceId, UUID viewerId, String acceptLanguage) {
        Grievance g = load(grievanceId);
        boolean internal = authorization.canViewInternal(viewerId, g);

        List<ActivityItem> items = new ArrayList<>();
        grievanceService.history(grievanceId).stream()
                .map(c -> internal ? HistoryEntry.full(c) : HistoryEntry.forCitizen(c, viewerId))
                .map(ActivityItem::status)
                .forEach(items::add);

        String previous = null;
        for (GrievanceAssignment a : assignments.findByGrievanceIdOrderByAssignedAt(grievanceId)) {
            items.add(ActivityItem.assignment(a, previous, internal));
            previous = a.getAssignee().getFullName();
        }

        for (GrievanceReroute r : reroutes.findWithDetailsByGrievanceId(grievanceId)) {
            boolean categoryChanged = !r.getOldCategory().getId().equals(r.getNewCategory().getId());
            items.add(ActivityItem.reroute(r,
                    categoryChanged ? categoryNames.nameOf(r.getOldCategory().getId(), acceptLanguage) : null,
                    categoryChanged ? categoryNames.nameOf(r.getNewCategory().getId(), acceptLanguage) : null,
                    internal));
        }

        (internal
                ? comments.findByGrievanceIdOrderByCreatedAt(grievanceId)
                : comments.findByGrievanceIdAndInternalOnlyFalseOrderByCreatedAt(grievanceId))
                .stream().map(ActivityItem::comment).forEach(items::add);

        // Stable: ties keep the order they were added in.
        items.sort(Comparator.comparing(ActivityItem::at));
        return items;
    }

    /**
     * A comment everyone sees needs ownership or GRIEVANCE_COMMENT in scope; an
     * internal remark needs GRIEVANCE_COMMENT, so a citizen can never post one.
     *
     * @throws NotAuthorizedException the actor may not post this kind of comment
     */
    @Transactional
    ActivityItem comment(UUID grievanceId, UUID actorId, String body, boolean internal) {
        Grievance g = load(grievanceId);
        Set<String> accepted = internal ? Set.of(COMMENT) : Set.of(CITIZEN_OWNER, COMMENT);
        if (!authorization.isAuthorized(actorId, g, accepted)) {
            throw new NotAuthorizedException(internal
                    ? "Only staff may post internal remarks on this grievance"
                    : "You may not comment on this grievance");
        }
        AppUser author = entityManager.find(AppUser.class, actorId);
        GrievanceComment comment = comments.save(new GrievanceComment(g, author, body.strip(), internal));
        // Pick up created_at, a DB default.
        comments.flush();
        entityManager.refresh(comment);
        return ActivityItem.comment(comment);
    }

    private Grievance load(UUID grievanceId) {
        return grievances.findById(grievanceId).orElseThrow(() -> new NotFoundException("Grievance", grievanceId));
    }
}
