import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { form, FormField, maxLength, submit } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { ActivityItem, TicketOverview, UserType } from '../../core/api/api.types';
import { GrievanceService } from '../../core/api/grievance.service';
import {
  HOLD_REASON_LABELS,
  IST_FORMAT,
  IST_ZONE,
  PRIORITY_LABELS,
  STATUS_LABELS,
} from '../../core/api/labels';
import { AuthService } from '../../core/auth/auth.service';

/** Which part of the activity feed is showing. */
type ActivityFilter = 'ALL' | 'COMMENTS' | 'HISTORY';

const MAX_COMMENT = 2000;

/**
 * One grievance as a ticket: description, attachments and the activity feed,
 * with a details panel beside it (below it on a phone). Citizens and staff
 * share it; the backend decides what each sees.
 */
@Component({
  selector: 'app-grievance-detail',
  imports: [DatePipe, FormField, RouterLink],
  templateUrl: './grievance-detail.html',
  styleUrl: './grievance-detail.scss',
})
export class GrievanceDetail implements OnInit {
  private readonly grievanceService = inject(GrievanceService);
  private readonly auth = inject(AuthService);

  /** Route parameter. */
  readonly id = input.required<string>();

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly holdReasonLabels = HOLD_REASON_LABELS;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly istFormat = IST_FORMAT;
  protected readonly istZone = IST_ZONE;
  protected readonly maxComment = MAX_COMMENT;

  protected readonly overview = signal<TicketOverview | null>(null);
  protected readonly activity = signal<ActivityItem[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly filter = signal<ActivityFilter>('ALL');
  protected readonly commentForm = form(signal({ body: '', internal: false }), (p) => {
    maxLength(p.body, MAX_COMMENT);
  });
  protected readonly draftLength = computed(() => this.commentForm.body().value().length);
  protected readonly internalDraft = computed(() => this.commentForm.internal().value());
  protected readonly posting = signal(false);
  protected readonly postError = signal<string | null>(null);

  protected readonly isCitizen = computed(() => this.auth.currentUser()?.type === 'CITIZEN');
  protected readonly back = computed(() =>
    this.isCitizen()
      ? { link: '/citizen', label: 'My grievances' }
      : { link: '/queue', label: 'Queue' },
  );

  /** Newest first: what changed last is what people come to see. */
  protected readonly feed = computed(() => {
    const filter = this.filter();
    return [...this.activity()]
      .reverse()
      .filter((item) => filter === 'ALL' || (filter === 'COMMENTS') === (item.kind === 'COMMENT'));
  });

  protected readonly commentCount = computed(
    () => this.activity().filter((item) => item.kind === 'COMMENT').length,
  );

  /** The note from the latest resolution, while it stands (resolvedAt is cleared on reopen and revert). */
  protected readonly resolutionNote = computed(() => {
    if (!this.overview()?.grievance.resolvedAt) {
      return null;
    }
    return (
      this.feed().find((i) => i.kind === 'STATUS' && i.toStatus === 'RESOLVED')?.reason ?? null
    );
  });

  /** Time left on the SLA, or how far past it. Null once the clock has stopped for good. */
  protected readonly sla = computed(() => {
    const g = this.overview()?.grievance;
    if (!g || ['RESOLVED', 'CLOSED', 'REJECTED'].includes(g.status)) {
      return null;
    }
    const hours = Math.round((Date.parse(g.slaDueAt) - Date.now()) / 3_600_000);
    if (hours < 0) {
      return { overdue: true, text: `Overdue by ${duration(-hours)}` };
    }
    return { overdue: false, text: `Due in ${duration(hours)}` };
  });

  protected readonly canPost = computed(
    () =>
      !this.posting() &&
      this.commentForm.body().value().trim() !== '' &&
      this.commentForm().valid(),
  );

  ngOnInit(): void {
    this.load();
  }

  protected async load(): Promise<void> {
    this.error.set(null);
    try {
      const [overview, activity] = await Promise.all([
        this.grievanceService.overview(this.id()),
        this.grievanceService.activity(this.id()),
      ]);
      this.overview.set(overview);
      this.activity.set(activity);
    } catch (e) {
      this.error.set(loadFailure(e));
    }
  }

  protected setInternal(internal: boolean): void {
    this.commentForm.internal().value.set(internal);
  }

  protected async post(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.canPost()) {
      return;
    }
    this.posting.set(true);
    this.postError.set(null);
    await submit(this.commentForm, async (f) => {
      const { body, internal } = f().value();
      try {
        const created = await this.grievanceService.comment(this.id(), {
          body: body.trim(),
          internal,
        });
        this.activity.update((items) => [...items, created]);
        f().value.set({ body: '', internal: false });
        f().reset();
      } catch (e) {
        this.postError.set(postFailure(e));
      }
      return undefined;
    });
    this.posting.set(false);
  }

  /**
   * Who acted. A citizen acting on a grievance is always its own citizen, so
   * to a citizen viewer that is "You".
   */
  protected actor(item: ActivityItem): string {
    if (item.actorType === 'CITIZEN' && this.isCitizen()) {
      return 'You';
    }
    return item.actorName ?? FALLBACK_NAMES[item.actorType];
  }

  protected initials(name: string | null): string {
    if (!name) {
      return '?';
    }
    const parts = name.trim().split(/\s+/);
    return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  }
}

const FALLBACK_NAMES: Record<UserType, string> = {
  CITIZEN: 'Citizen',
  STAFF: 'Municipal staff',
  SYSTEM: 'System',
};

function duration(hours: number): string {
  if (hours < 1) {
    return 'under an hour';
  }
  if (hours < 48) {
    return `${hours} ${hours === 1 ? 'hour' : 'hours'}`;
  }
  return `${Math.round(hours / 24)} days`;
}

function loadFailure(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    // 404 alike for no such grievance and one the viewer may not see.
    if (e.status === 404) {
      return 'This grievance could not be found.';
    }
    if (e.status === 0) {
      return 'No connection. Check your mobile data and try again.';
    }
  }
  return 'Something went wrong. Please try again.';
}

function postFailure(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) {
      return 'Not sent: no connection. Your comment is kept, try again.';
    }
    if (e.status === 403) {
      return 'You cannot post this comment.';
    }
  }
  return 'Not sent. Your comment is kept, try again.';
}
