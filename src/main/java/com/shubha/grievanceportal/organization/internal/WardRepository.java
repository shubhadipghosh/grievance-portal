package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.Ward;
import com.shubha.grievanceportal.organization.WardSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WardRepository extends JpaRepository<Ward, UUID> {

    Optional<Ward> findByCode(String code);

    @Query("""
            select new com.shubha.grievanceportal.organization.WardSummary(w.id, w.name)
            from Ward w
            where w.active = true
            order by w.name
            """)
    List<WardSummary> findActiveSummaries();

    @Query("""
            select new com.shubha.grievanceportal.organization.WardSummary(w.id, w.name)
            from Ward w
            where w.active = true
              and w.id in :ids
            order by w.name
            """)
    List<WardSummary> findActiveSummaries(@Param("ids") Collection<UUID> ids);
}
