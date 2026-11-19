import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { form, FormField, maxLength } from '@angular/forms/signals';
import {
  GrievanceStatus,
  HoldReasonType,
  StaffMember,
  TicketAction,
} from '../../core/api/api.types';
import { GrievanceService, TransitionPath } from '../../core/api/grievance.service';

const MAX_REASON = 1000;
// grievance_assignment.reason is VARCHAR(500).
const MAX_ASSIGN_REASON = 500;
const MAX_HOLD_OWNER_NOTE = 255;

/** Endpoints for the targets that take only a reason. ASSIGNED and ON_HOLD have their own. */
const PATHS: Partial<Record<GrievanceStatus, TransitionPath>> = {
  ACKNOWLEDGED: 'acknowledge',
  IN_PROGRESS: 'in-progress',
  RESOLVED: 'resolve',
  REJECTED: 'reject',
  CLOSED: 'close',
  REOPENED: 'reopen',
};

/** The inputs every action's form may use; each action shows only its own. */
const EMPTY = {
  reason: '',
  assigneeId: '',
  holdReason: '' as HoldReasonType | '',
  holdOwnerNote: '',
};

/** Staff wording; HOLD_REASON_LABELS speaks to the citizen. */
const HOLD_REASONS: { value: HoldReasonType; label: string }[] = [
  { value: 'AWAITING_CITIZEN', label: 'Waiting on the citizen (pauses the SLA)' },
  { value: 'AWAITING_CONTRACTOR', label: 'Waiting on a contractor' },
  { value: 'AWAITING_DEPARTMENT', label: 'Waiting on another department' },
  { value: 'AWAITING_BUDGET', label: 'Waiting on budget approval' },
  { value: 'OTHER', label: 'Other' },
];

/**
 * The status-change buttons on the ticket page. The backend lists what the
 * viewer may do; this only words it and collects the inputs. One action is
 * open at a time, as an inline form below the buttons.
 */
@Component({
  selector: 'app-ticket-actions',
  imports: [FormField],
  templateUrl: './ticket-actions.html',
  styleUrl: './ticket-actions.scss',
})
export class TicketActions {
  private readonly grievanceService = inject(GrievanceService);

  readonly grievanceId = input.required<string>();
  readonly status = input.required<GrievanceStatus>();
  readonly actions = input.required<TicketAction[]>();
  readonly isCitizen = input(false);

  /** The grievance changed (or turned out to have changed): reload the page. */
  readonly changed = output<void>();

  protected readonly holdReasons = HOLD_REASONS;

  protected readonly open = signal<TicketAction | null>(null);
  protected readonly actionForm = form(signal(EMPTY), (p) => {
    maxLength(p.holdOwnerNote, MAX_HOLD_OWNER_NOTE);
  });
  protected readonly reason = computed(() => this.actionForm.reason().value());
  private readonly holdReason = computed(() => this.actionForm.holdReason().value());
  protected readonly assignees = signal<StaffMember[] | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly maxReason = computed(() =>
    this.open()?.to === 'ASSIGNED' ? MAX_ASSIGN_REASON : MAX_REASON,
  );

  /** An external party must be named unless the hold waits on the citizen. */
  protected readonly needsOwnerNote = computed(
    () => this.holdReason() !== '' && this.holdReason() !== 'AWAITING_CITIZEN',
  );

  protected readonly canSubmit = computed(() => {
    const action = this.open();
    if (!action || this.saving()) {
      return false;
    }
    const reason = this.reason().trim();
    if ((action.reasonRequired && reason === '') || reason.length > this.maxReason()) {
      return false;
    }
    if (action.to === 'ASSIGNED') {
      return this.actionForm.assigneeId().value() !== '';
    }
    if (action.to === 'ON_HOLD') {
      const note = this.actionForm.holdOwnerNote().value().trim();
      return (
        this.holdReason() !== '' &&
        (!this.needsOwnerNote() || (note !== '' && note.length <= MAX_HOLD_OWNER_NOTE))
      );
    }
    return true;
  });

  /** One label per (current status, target): three different transitions lead to IN_PROGRESS. */
  protected label(action: TicketAction): string {
    switch (action.to) {
      case 'ACKNOWLEDGED':
        return 'Acknowledge';
      case 'ASSIGNED':
        return this.status() === 'REOPENED' ? 'Reassign' : 'Assign';
      case 'IN_PROGRESS':
        return this.status() === 'ON_HOLD'
          ? 'Resume work'
          : this.status() === 'RESOLVED'
            ? 'Undo resolution'
            : 'Start work';
      case 'ON_HOLD':
        return 'Put on hold';
      case 'RESOLVED':
        return 'Mark resolved';
      case 'REJECTED':
        return 'Reject';
      case 'CLOSED':
        return this.isCitizen() ? 'Confirm and close' : 'Close';
      case 'REOPENED':
        return 'Reopen';
      default:
        return action.to;
    }
  }

  /** What the reason box is called for this action. */
  protected reasonLabel(action: TicketAction): string {
    if (action.to === 'RESOLVED') {
      return 'How it was resolved';
    }
    if (action.to === 'REOPENED') {
      return 'Why it is not fixed';
    }
    return action.reasonRequired ? 'Reason' : 'Note (optional)';
  }

  /** Rejecting is final for staff, so it is styled apart from the rest. */
  protected isDanger(action: TicketAction): boolean {
    return action.to === 'REJECTED';
  }

  protected async start(action: TicketAction): Promise<void> {
    this.open.set(action);
    this.actionForm().value.set(EMPTY);
    this.actionForm().reset();
    this.error.set(null);
    if (action.to === 'ASSIGNED' && this.assignees() === null) {
      try {
        this.assignees.set(await this.grievanceService.assignees(this.grievanceId()));
      } catch (e) {
        this.error.set(failure(e));
      }
    }
  }

  protected cancel(): void {
    this.open.set(null);
    this.error.set(null);
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    const action = this.open();
    if (!action || !this.canSubmit()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const id = this.grievanceId();
    const { assigneeId, holdOwnerNote } = this.actionForm().value();
    const reason = this.reason().trim() || null;
    try {
      if (action.to === 'ASSIGNED') {
        await this.grievanceService.assign(id, assigneeId, reason);
      } else if (action.to === 'ON_HOLD') {
        const holdReason = this.holdReason() as HoldReasonType;
        const citizen = holdReason === 'AWAITING_CITIZEN';
        await this.grievanceService.hold(id, {
          reasonType: holdReason,
          ownerType: citizen ? 'CITIZEN' : 'EXTERNAL',
          holdOwnerId: null,
          holdOwnerNote: citizen ? null : holdOwnerNote.trim(),
          reason,
        });
      } else {
        await this.grievanceService.transition(id, PATHS[action.to]!, reason);
      }
      this.open.set(null);
      this.assignees.set(null);
      this.changed.emit();
    } catch (e) {
      this.error.set(failure(e));
      // Someone else moved it first: show the page as it now stands.
      if (e instanceof HttpErrorResponse && e.status === 409) {
        this.open.set(null);
        this.changed.emit();
      }
    }
    this.saving.set(false);
  }
}

function failure(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    switch (e.status) {
      case 0:
        return 'Not saved: no connection. Try again.';
      case 403:
        return 'You are not allowed to do this on this grievance.';
      case 409:
        return 'This grievance changed while you were looking at it. It has been reloaded.';
      case 400:
      case 422:
        return 'Not saved. Check the details and try again.';
    }
  }
  return 'Not saved. Try again.';
}
