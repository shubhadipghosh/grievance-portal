package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Finders are shaped to the indexes in V1. The partial-index queries spell out
 * the excluded statuses as literals: Postgres only uses a partial index when it
 * can prove the query's WHERE implies the index's WHERE, which a bound
 * parameter cannot do. Keep these lists identical to the index predicates.
 */
public interface GrievanceRepository extends JpaRepository<Grievance, UUID> {

    Optional<Grievance> findByTicketNo(String ticketNo);

    /** Raw value for building ticket numbers like GRV-2026-000123. */
    @Query(value = "select nextval('grievance_ticket_seq')", nativeQuery = true)
    long nextTicketSequence();

    /**
     * Citizen "my grievances" page; idx_grv_citizen. Fetches the ward: each row
     * shows its name. Ties on submittedAt (one transaction) fall back to the
     * id, which is UUIDv7 and so also newest first.
     */
    @Query(value = """
            select g from Grievance g
            join fetch g.ward
            where g.citizen.id = :citizenId
            order by g.submittedAt desc, g.id desc
            """,
            countQuery = "select count(g) from Grievance g where g.citizen.id = :citizenId")
    Page<Grievance> findByCitizenIdOrderBySubmittedAtDesc(@Param("citizenId") UUID citizenId, Pageable pageable);

    /**
     * Department queue filtered by one open status, limited to what the actor
     * can view (GrievanceAuthorization.canView): the visible wards, plus the
     * actor's own grievances if they are an ACTIVE citizen. idx_grv_dept_open.
     * Fetches ward and assignee in the same query: each row shows their names.
     */
    @Query("""
            select g from Grievance g
            join fetch g.ward
            left join fetch g.currentAssignee
            where g.department.id = :departmentId
              and g.status = :status
              and g.status not in (
                  com.shubha.grievanceportal.grievance.GrievanceStatus.CLOSED,
                  com.shubha.grievanceportal.grievance.GrievanceStatus.REJECTED)
              and (:allWards = true
                   or g.ward.id in :wardIds
                   or (g.citizen.id = :actorId
                       and g.citizen.status = com.shubha.grievanceportal.identity.UserStatus.ACTIVE))
            order by g.slaDueAt
            """)
    Page<Grievance> findOpenVisibleByDepartmentAndStatus(@Param("actorId") UUID actorId,
                                                         @Param("allWards") boolean allWards,
                                                         @Param("wardIds") Collection<UUID> wardIds,
                                                         @Param("departmentId") UUID departmentId,
                                                         @Param("status") GrievanceStatus status,
                                                         Pageable pageable);

    /** Field officer "my work"; idx_grv_assignee. */
    @Query("""
            select g from Grievance g
            where g.currentAssignee.id = :assigneeId
              and g.status not in (
                  com.shubha.grievanceportal.grievance.GrievanceStatus.CLOSED,
                  com.shubha.grievanceportal.grievance.GrievanceStatus.REJECTED,
                  com.shubha.grievanceportal.grievance.GrievanceStatus.RESOLVED)
            order by g.slaDueAt
            """)
    Page<Grievance> findOpenByAssignee(@Param("assigneeId") UUID assigneeId, Pageable pageable);

    /** SLA breach job: running-clock grievances due before the cutoff; idx_grv_sla_due. */
    @Query("""
            select g from Grievance g
            where g.slaDueAt < :cutoff
              and g.status not in (
                  com.shubha.grievanceportal.grievance.GrievanceStatus.CLOSED,
                  com.shubha.grievanceportal.grievance.GrievanceStatus.REJECTED,
                  com.shubha.grievanceportal.grievance.GrievanceStatus.RESOLVED,
                  com.shubha.grievanceportal.grievance.GrievanceStatus.ON_HOLD)
            order by g.slaDueAt
            """)
    List<Grievance> findSlaDueBefore(@Param("cutoff") OffsetDateTime cutoff, Limit limit);
}
