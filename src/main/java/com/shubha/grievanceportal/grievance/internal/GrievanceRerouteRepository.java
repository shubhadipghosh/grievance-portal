package com.shubha.grievanceportal.grievance.internal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Read-only: extends the bare {@link Repository} so nothing can modify the audit. */
public interface GrievanceRerouteRepository extends Repository<GrievanceReroute, UUID> {

    // idx_reroute_grv
    @Query("""
            select r from GrievanceReroute r
            join fetch r.actor
            join fetch r.oldDepartment
            join fetch r.newDepartment
            where r.grievance.id = :grievanceId
            order by r.occurredAt, r.id
            """)
    List<GrievanceReroute> findWithDetailsByGrievanceId(@Param("grievanceId") UUID grievanceId);
}
