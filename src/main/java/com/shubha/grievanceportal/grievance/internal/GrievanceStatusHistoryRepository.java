package com.shubha.grievanceportal.grievance.internal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Append-only: extends the bare {@link Repository} so no delete or bulk
 * methods are exposed. Only insert and read.
 */
public interface GrievanceStatusHistoryRepository extends Repository<GrievanceStatusHistory, UUID> {

    GrievanceStatusHistory save(GrievanceStatusHistory entry);

    // idx_hist_grv
    List<GrievanceStatusHistory> findByGrievanceIdOrderByOccurredAt(UUID grievanceId);

    /**
     * With each actor, for their name and type. occurred_at is the transaction
     * start, so changes in one transaction tie; the UUIDv7 id keeps them in
     * insertion order.
     */
    @Query("""
            select h from GrievanceStatusHistory h
            join fetch h.actor
            where h.grievance.id = :grievanceId
            order by h.occurredAt, h.id
            """)
    List<GrievanceStatusHistory> findWithActorsByGrievanceId(@Param("grievanceId") UUID grievanceId);
}
