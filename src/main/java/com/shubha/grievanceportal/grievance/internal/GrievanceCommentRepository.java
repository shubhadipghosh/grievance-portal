package com.shubha.grievanceportal.grievance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GrievanceCommentRepository extends JpaRepository<GrievanceComment, UUID> {

    // idx_comment_grv. Staff view: includes internal-only comments.
    List<GrievanceComment> findByGrievanceIdOrderByCreatedAt(UUID grievanceId);

    // Citizen view: never includes internal-only comments.
    List<GrievanceComment> findByGrievanceIdAndInternalOnlyFalseOrderByCreatedAt(UUID grievanceId);
}
