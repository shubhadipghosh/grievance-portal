package com.shubha.grievanceportal.grievance.internal;

import org.springframework.data.repository.Repository;

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
}
