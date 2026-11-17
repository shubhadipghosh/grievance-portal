import { GrievanceStatus, HoldReasonType, Priority } from './api.types';

export const STATUS_LABELS: Record<GrievanceStatus, string> = {
  SUBMITTED: 'Submitted',
  ACKNOWLEDGED: 'Acknowledged',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  REJECTED: 'Rejected',
  REOPENED: 'Reopened',
};

export const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: 'Low',
  NORMAL: 'Normal',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

export const HOLD_REASON_LABELS: Record<HoldReasonType, string> = {
  AWAITING_CITIZEN: 'Awaiting your response',
  AWAITING_CONTRACTOR: 'Awaiting contractor',
  AWAITING_DEPARTMENT: 'Awaiting another department',
  AWAITING_BUDGET: 'Awaiting budget approval',
  OTHER: 'Other',
};

/** DatePipe arguments for citizen-facing times: India Standard Time, labelled. */
export const IST_FORMAT = "d MMM y, h:mm a 'IST'";
export const IST_ZONE = '+0530';
