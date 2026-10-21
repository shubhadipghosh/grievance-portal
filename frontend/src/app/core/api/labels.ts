import { GrievanceStatus, HoldReasonType, UserType } from './api.types';

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

export const HOLD_REASON_LABELS: Record<HoldReasonType, string> = {
  AWAITING_CITIZEN: 'Awaiting your response',
  AWAITING_CONTRACTOR: 'Awaiting contractor',
  AWAITING_DEPARTMENT: 'Awaiting another department',
  AWAITING_BUDGET: 'Awaiting budget approval',
  OTHER: 'Other',
};

/**
 * Who acted, as a citizen is told it. Never an officer's name: see
 * STATE_MACHINE.md, "What citizens see". CITIZEN is always the viewer, since
 * only the citizen who filed a grievance can act on it as a citizen.
 */
export const CITIZEN_ACTOR_LABELS: Record<UserType, string> = {
  CITIZEN: 'You',
  STAFF: 'Municipal staff',
  SYSTEM: 'System',
};

/** DatePipe arguments for citizen-facing times: India Standard Time, labelled. */
export const IST_FORMAT = "d MMM y, h:mm a 'IST'";
export const IST_ZONE = '+0530';
