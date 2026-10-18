package com.shubha.grievanceportal.grievance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GrievanceAssignmentRepository extends JpaRepository<GrievanceAssignment, UUID> {

    // idx_assign_grv
    List<GrievanceAssignment> findByGrievanceIdOrderByAssignedAt(UUID grievanceId);
}
