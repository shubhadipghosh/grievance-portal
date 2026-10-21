import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { form, FormField, required, submit, validate } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { CitizenGrievanceSummary } from '../../core/api/api.types';
import { GrievanceService } from '../../core/api/grievance.service';
import { IST_FORMAT, IST_ZONE, STATUS_LABELS } from '../../core/api/labels';

const TICKET_FORMAT = 'Ticket numbers look like GRV-2026-000123.';

/** The citizen's own grievances, newest first, and a lookup by ticket number. */
@Component({
  selector: 'app-citizen-home',
  imports: [DatePipe, FormField, RouterLink],
  templateUrl: './citizen-home.html',
  styleUrl: './citizen-home.scss',
})
export class CitizenHome implements OnInit {
  private readonly grievanceService = inject(GrievanceService);
  private readonly router = inject(Router);

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly istFormat = IST_FORMAT;
  protected readonly istZone = IST_ZONE;

  /** Null until the first page arrives. */
  protected readonly grievances = signal<CitizenGrievanceSummary[] | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(false);
  protected readonly listError = signal(false);
  private nextPage = 0;

  protected readonly lookupForm = form(signal({ ticketNo: '' }), (p) => {
    required(p.ticketNo, { message: 'Enter a ticket number.' });
    validate(p.ticketNo, ({ value }) =>
      value() === '' || /^GRV-\d{4}-\d{6}$/.test(normalizeTicket(value()))
        ? undefined
        : { kind: 'ticketNo', message: TICKET_FORMAT },
    );
  });
  protected readonly lookupError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadMore();
  }

  /** Appends the next page. "Show more" rather than page numbers: thumb-friendly. */
  protected async loadMore(): Promise<void> {
    this.loading.set(true);
    this.listError.set(false);
    try {
      const result = await this.grievanceService.mine(this.nextPage);
      this.grievances.update((shown) => [...(shown ?? []), ...result.content]);
      this.nextPage = result.page.number + 1;
      this.hasMore.set(this.nextPage < result.page.totalPages);
    } catch {
      this.listError.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  protected async onLookup(event: Event): Promise<void> {
    event.preventDefault();
    this.lookupError.set(null);
    await submit(this.lookupForm, async (f) => {
      try {
        const grievance = await this.grievanceService.byTicketNo(normalizeTicket(f().value().ticketNo));
        await this.router.navigate(['/citizen/grievances', grievance.id]);
      } catch (e) {
        this.lookupError.set(lookupFailure(e));
      }
      return undefined;
    });
  }
}

/** " grv-2026-000123 " → "GRV-2026-000123": typed on a phone keyboard, often lower case. */
function normalizeTicket(raw: string): string {
  return raw.trim().toUpperCase();
}

function lookupFailure(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    // The backend answers 404 alike for no such ticket and someone else's.
    if (e.status === 404) {
      return 'None of your grievances has that ticket number. Check it and try again.';
    }
    if (e.status === 0) {
      return 'No connection. Check your mobile data and try again.';
    }
  }
  return 'Something went wrong. Please try again.';
}
