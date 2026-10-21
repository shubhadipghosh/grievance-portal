import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GrievanceDetails, HistoryEntry } from '../../../core/api/api.types';
import { GrievanceService } from '../../../core/api/grievance.service';
import {
  CITIZEN_ACTOR_LABELS,
  HOLD_REASON_LABELS,
  IST_FORMAT,
  IST_ZONE,
  STATUS_LABELS,
} from '../../../core/api/labels';

/** One grievance as its citizen sees it: status, dates in IST, and what happened when. */
@Component({
  selector: 'app-citizen-grievance',
  imports: [DatePipe, RouterLink],
  templateUrl: './citizen-grievance.html',
  styleUrl: './citizen-grievance.scss',
})
export class CitizenGrievance implements OnInit {
  private readonly grievanceService = inject(GrievanceService);

  /** Route parameter. */
  readonly id = input.required<string>();

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly holdReasonLabels = HOLD_REASON_LABELS;
  protected readonly actorLabels = CITIZEN_ACTOR_LABELS;
  protected readonly istFormat = IST_FORMAT;
  protected readonly istZone = IST_ZONE;

  protected readonly grievance = signal<GrievanceDetails | null>(null);
  protected readonly history = signal<HistoryEntry[]>([]);
  protected readonly error = signal<string | null>(null);

  /** Newest first: what changed last is what the citizen came to see. */
  protected readonly timeline = computed(() => [...this.history()].reverse());

  /**
   * The note from the latest resolution, while it stands. resolvedAt is
   * cleared on reopen and revert, so an overturned resolution is not shown here.
   */
  protected readonly resolutionNote = computed(() => {
    if (!this.grievance()?.resolvedAt) {
      return null;
    }
    return this.timeline().find((h) => h.toStatus === 'RESOLVED')?.reason ?? null;
  });

  ngOnInit(): void {
    this.load();
  }

  protected async load(): Promise<void> {
    this.error.set(null);
    try {
      const [grievance, history] = await Promise.all([
        this.grievanceService.get(this.id()),
        this.grievanceService.history(this.id()),
      ]);
      this.grievance.set(grievance);
      this.history.set(history);
    } catch (e) {
      this.error.set(loadFailure(e));
    }
  }
}

function loadFailure(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    // 404 alike for no such grievance and someone else's.
    if (e.status === 404) {
      return 'This grievance could not be found.';
    }
    if (e.status === 0) {
      return 'No connection. Check your mobile data and try again.';
    }
  }
  return 'Something went wrong. Please try again.';
}
