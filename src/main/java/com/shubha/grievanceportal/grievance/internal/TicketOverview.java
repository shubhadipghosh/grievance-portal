package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.grievance.GrievanceDetails;

import java.util.List;

/**
 * The ticket page's header and side panel: the grievance plus the names its
 * ids stand for, and what the viewer may do in the comment box.
 *
 * @param categoryName       in the viewer's language; null if it has no translation
 * @param reporterName       the citizen who filed it; null if they have not given a name
 * @param assigneeName       null while unassigned
 * @param departmentAdmins   DEPT_ADMINs whose scope covers the grievance; may be empty
 * @param canComment         may post a comment everyone sees
 * @param canCommentInternal may post an internal remark (staff with GRIEVANCE_COMMENT)
 */
record TicketOverview(
        GrievanceDetails grievance,
        String categoryName,
        String departmentName,
        String wardName,
        String reporterName,
        String assigneeName,
        List<String> departmentAdmins,
        boolean canComment,
        boolean canCommentInternal) {
}
