package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.DepartmentSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByCode(String code);

    @Query("""
            select new com.shubha.grievanceportal.organization.DepartmentSummary(d.id, d.name)
            from Department d
            where d.active = true
            order by d.name
            """)
    List<DepartmentSummary> findActiveSummaries();

    @Query("""
            select new com.shubha.grievanceportal.organization.DepartmentSummary(d.id, d.name)
            from Department d
            where d.active = true
              and d.id in :ids
            order by d.name
            """)
    List<DepartmentSummary> findActiveSummaries(@Param("ids") Collection<UUID> ids);
}
