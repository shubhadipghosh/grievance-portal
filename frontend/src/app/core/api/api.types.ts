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
