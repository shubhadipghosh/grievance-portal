// Mirrors the backend records. Keep field names identical to the JSON.

/** identity.UserType */
export type UserType = 'CITIZEN' | 'STAFF' | 'SYSTEM';

/** identity.CurrentUser, returned by /auth/me and both login endpoints. */
export interface CurrentUser {
  id: string;
  type: UserType;
}

/** AuthController.StaffLoginRequest */
export interface StaffLoginRequest {
  email: string;
  password: string;
}

/** AuthController.OtpSendRequest. phone is E.164: +91 then 10 digits. */
export interface OtpSendRequest {
  phone: string;
}

/** AuthController.OtpVerifyRequest */
export interface OtpVerifyRequest {
  phone: string;
  code: string;
}

/** grievance.GrievanceStatus */
export type GrievanceStatus =
  | 'SUBMITTED'
  | 'ACKNOWLEDGED'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'ON_HOLD'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REJECTED'
  | 'REOPENED';

/** grievance.Priority */
export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';

/** grievance.GrievanceSummary. UUIDs as strings; timestamps are ISO-8601 with offset. */
export interface GrievanceSummary {
  id: string;
  ticketNo: string;
  status: GrievanceStatus;
  priority: Priority;
  departmentId: string;
  wardId: string;
  wardName: string;
  title: string;
  currentAssigneeId: string | null;
  assigneeName: string | null;
  slaDueAt: string;
  /** Null unless resolved. Cleared on reopen and on revert, so it is the latest resolution only. */
  resolvedAt: string | null;
}

/** grievance.HoldReasonType */
export type HoldReasonType =
  'AWAITING_CITIZEN' | 'AWAITING_CONTRACTOR' | 'AWAITING_DEPARTMENT' | 'AWAITING_BUDGET' | 'OTHER';

/** grievance.HoldOwnerType */
export type HoldOwnerType = 'CITIZEN' | 'STAFF' | 'EXTERNAL';

/** grievance.NewGrievance. Limits match the backend's @Size. */
export interface NewGrievance {
  categoryId: string;
  wardId: string;
  /** Max 200. */
  title: string;
  /** Max 5000. */
  description: string;
  /** Max 500. */
  addressText: string | null;
  latitude: number | null;
  longitude: number | null;
}

/** grievance.GrievanceDetails. Nullable fields are null when not applicable. */
export interface GrievanceDetails {
  id: string;
  ticketNo: string;
  status: GrievanceStatus;
  priority: Priority;
  categoryId: string;
  departmentId: string;
  wardId: string;
  title: string;
  description: string;
  addressText: string | null;
  latitude: number | null;
  longitude: number | null;
  currentAssigneeId: string | null;
  slaDueAt: string;
  submittedAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
  reopenCount: number;
  holdReasonType: HoldReasonType | null;
  holdOwnerType: HoldOwnerType | null;
  holdSince: string | null;
}

/** grievance.CitizenGrievanceSummary: a row in the citizen's own list. No staff detail. */
export interface CitizenGrievanceSummary {
  id: string;
  ticketNo: string;
  status: GrievanceStatus;
  title: string;
  wardName: string;
  submittedAt: string;
  /** Null unless resolved. Cleared on reopen and on revert. */
  resolvedAt: string | null;
}

/**
 * grievance.internal.TicketOverview: the ticket page's header and side panel.
 * departmentAdmins are the DEPT_ADMINs whose scope covers the grievance.
 */
export interface TicketOverview {
  grievance: GrievanceDetails;
  /** In the browser's language; null if the category has no translation. */
  categoryName: string | null;
  departmentName: string;
  wardName: string;
  /** Null for a citizen who has not given a name. */
  reporterName: string | null;
  /** Null while unassigned. */
  assigneeName: string | null;
  departmentAdmins: string[];
  /** May post a comment everyone sees. */
  canComment: boolean;
  /** May post an internal remark: staff only. */
  canCommentInternal: boolean;
}

/** grievance.internal.ActivityItem.Kind */
export type ActivityKind = 'COMMENT' | 'STATUS' | 'ASSIGNMENT' | 'REROUTE';

/**
 * grievance.internal.ActivityItem: a comment or an event. Fields that do not
 * apply to the kind are null. Staff notes (reason) and internal remarks are
 * only sent to staff.
 */
export interface ActivityItem {
  kind: ActivityKind;
  at: string;
  actorType: UserType;
  /** Null only for a citizen who has not given a name. */
  actorName: string | null;
  /** COMMENT */
  body: string | null;
  /** COMMENT: an internal remark, never sent to a citizen. */
  internal: boolean | null;
  /** STATUS. Null fromStatus is the filing. */
  fromStatus: GrievanceStatus | null;
  toStatus: GrievanceStatus | null;
  holdReasonType: HoldReasonType | null;
  /** STATUS, ASSIGNMENT, REROUTE */
  reason: string | null;
  /** ASSIGNMENT. previousAssigneeName is null on the first assignment. */
  assigneeName: string | null;
  previousAssigneeName: string | null;
  /** REROUTE. Each pair is null when that one did not change. */
  fromDepartment: string | null;
  toDepartment: string | null;
  fromCategory: string | null;
  toCategory: string | null;
}

/** GrievanceController.CommentRequest. body: max 2000. */
export interface NewComment {
  body: string;
  internal: boolean;
}

/** organization.DepartmentSummary */
export interface DepartmentSummary {
  id: string;
  name: string;
}

/** organization.WardSummary */
export interface WardSummary {
  id: string;
  name: string;
}

/**
 * organization.CategorySummary. name is in the browser's language, or English
 * when there is no translation. Only a category no other lists as its parent
 * may be filed against.
 */
export interface CategorySummary {
  id: string;
  parentId: string | null;
  name: string;
}

/** Spring Data's PagedModel. page.number is zero-based. */
export interface PagedModel<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** ProfileService.Profile. fullName is null for a citizen who has not given one yet. */
export interface Profile {
  type: UserType;
  fullName: string | null;
  phone: string | null;
  email: string | null;
  preferredLocale: Locale;
}

/** ProfileController.ProfileUpdateRequest */
export interface ProfileUpdateRequest {
  fullName: string;
  preferredLocale: Locale;
}

/** The languages category names are translated into (V9). */
export type Locale = 'en' | 'bn';
