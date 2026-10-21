package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.DepartmentSummary;
import com.shubha.grievanceportal.organization.ReferenceDataAccess;
import com.shubha.grievanceportal.organization.WardSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Departments and wards for dropdowns, limited to what the caller may see
 * (see {@link ReferenceDataAccess}). Inactive entries are never listed.
 */
@RestController
@RequestMapping("/api/v1")
class ReferenceDataController {

    private final DepartmentRepository departments;
    private final WardRepository wards;
    private final ReferenceDataAccess access;

    ReferenceDataController(DepartmentRepository departments, WardRepository wards, ReferenceDataAccess access) {
        this.departments = departments;
        this.wards = wards;
        this.access = access;
    }

    @GetMapping("/departments")
    List<DepartmentSummary> departments(Principal principal) {
        ReferenceDataAccess.Visible visible = access.departmentsVisibleTo(userId(principal));
        return visible.all() ? departments.findActiveSummaries() : departments.findActiveSummaries(visible.ids());
    }

    @GetMapping("/wards")
    List<WardSummary> wards(Principal principal) {
        ReferenceDataAccess.Visible visible = access.wardsVisibleTo(userId(principal));
        return visible.all() ? wards.findActiveSummaries() : wards.findActiveSummaries(visible.ids());
    }

    // The principal is identity's CurrentUser, whose name is the user id. Read
    // through java.security.Principal so this module does not depend on identity.
    private static UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
