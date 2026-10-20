package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceDetails;
import com.shubha.grievanceportal.grievance.GrievanceService;
import com.shubha.grievanceportal.grievance.GrievanceStatus;
import com.shubha.grievanceportal.grievance.GrievanceSummary;
import com.shubha.grievanceportal.grievance.NewGrievance;
import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.UserStatus;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Ward;
import com.shubha.grievanceportal.shared.InvalidReferenceException;
import com.shubha.grievanceportal.shared.NotAuthorizedException;
import com.shubha.grievanceportal.shared.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class GrievanceServiceImpl implements GrievanceService {

    private final GrievanceRepository grievances;
    private final GrievanceStatusHistoryRepository history;
    private final GrievanceAuthorization authorization;
    private final EntityManager entityManager;
    private final ZoneId displayZone;

    GrievanceServiceImpl(GrievanceRepository grievances, GrievanceStatusHistoryRepository history,
                         GrievanceAuthorization authorization, EntityManager entityManager,
                         @Value("${app.display-zone:Asia/Kolkata}") ZoneId displayZone) {
        this.grievances = grievances;
        this.history = history;
        this.authorization = authorization;
        this.entityManager = entityManager;
        this.displayZone = displayZone;
    }

    /** The year follows the citizen's calendar (display zone), not the JVM's UTC clock. */
    static String ticketNo(Instant filedAt, ZoneId displayZone, long sequence) {
        return "GRV-%d-%06d".formatted(filedAt.atZone(displayZone).getYear(), sequence);
    }

    @Override
    @Transactional
    public GrievanceDetails file(UUID citizenId, NewGrievance request) {
        Objects.requireNonNull(citizenId, "citizenId");
        AppUser citizen = entityManager.find(AppUser.class, citizenId);
        if (citizen == null || citizen.getUserType() != UserType.CITIZEN || citizen.getStatus() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("Only an active citizen may file a grievance");
        }
        Category category = entityManager.find(Category.class, request.categoryId());
        if (category == null || !category.isActive()) {
            throw new InvalidReferenceException("category", request.categoryId());
        }
        Ward ward = entityManager.find(Ward.class, request.wardId());
        if (ward == null || !ward.isActive()) {
            throw new InvalidReferenceException("ward", request.wardId());
        }

        OffsetDateTime now = OffsetDateTime.now();
        String ticketNo = ticketNo(now.toInstant(), displayZone, grievances.nextTicketSequence());
        Grievance grievance = grievances.save(new Grievance(ticketNo, citizen, category, ward,
                category.getDefaultDepartment(), request.title(), request.description(), request.addressText(),
                request.latitude(), request.longitude(), now.plusHours(category.getSlaHours())));
        // The audit trail starts at filing: the initial row has no from-status.
        history.save(new GrievanceStatusHistory(grievance, null, GrievanceStatus.SUBMITTED, citizen, null));

        // Pick up DB defaults (submitted_at, created_at) for the response.
        grievances.flush();
        entityManager.refresh(grievance);
        return details(grievance);
    }

    @Override
    public GrievanceDetails get(UUID grievanceId) {
        return grievances.findById(grievanceId)
                .map(GrievanceServiceImpl::details)
                .orElseThrow(() -> new NotFoundException("Grievance", grievanceId));
    }

    @Override
    public GrievanceDetails getByTicketNo(String ticketNo) {
        return grievances.findByTicketNo(ticketNo)
                .map(GrievanceServiceImpl::details)
                .orElseThrow(() -> new NotFoundException("Grievance", ticketNo));
    }

    @Override
    public Page<GrievanceSummary> queue(UUID actorId, UUID departmentId, GrievanceStatus status,
                                        Pageable pageable) {
        Objects.requireNonNull(actorId, "actorId");
        GrievanceAuthorization.VisibleWards visible = authorization.visibleWards(actorId, departmentId);
        // The query fixes the order (SLA deadline); a client-supplied sort is dropped.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return grievances.findOpenVisibleByDepartmentAndStatus(actorId, visible.all(), visible.wardIds(),
                        departmentId, status, unsorted)
                .map(GrievanceServiceImpl::summary);
    }

    private static GrievanceDetails details(Grievance g) {
        return new GrievanceDetails(g.getId(), g.getTicketNo(), g.getStatus(), g.getPriority(),
                g.getCategory().getId(), g.getDepartment().getId(), g.getWard().getId(),
                g.getTitle(), g.getDescription(), g.getAddressText(), g.getLatitude(), g.getLongitude(),
                idOf(g.getCurrentAssignee()), g.getSlaDueAt(), g.getSubmittedAt(), g.getResolvedAt(),
                g.getClosedAt(), g.getReopenCount(), g.getHoldReasonType(), g.getHoldOwnerType(),
                g.getHoldSince());
    }

    private static GrievanceSummary summary(Grievance g) {
        return new GrievanceSummary(g.getId(), g.getTicketNo(), g.getStatus(), g.getPriority(),
                g.getDepartment().getId(), g.getWard().getId(), g.getTitle(),
                idOf(g.getCurrentAssignee()), g.getSlaDueAt());
    }

    private static UUID idOf(AppUser user) {
        return user == null ? null : user.getId();
    }
}
