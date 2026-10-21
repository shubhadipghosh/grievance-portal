import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { GrievanceDetails, HistoryEntry } from '../../../core/api/api.types';
import { CitizenGrievance } from './citizen-grievance';

function details(overrides: Partial<GrievanceDetails> = {}): GrievanceDetails {
  return {
    id: 'g1',
    ticketNo: 'GRV-2026-000042',
    status: 'RESOLVED',
    title: 'Garbage not collected',
    submittedAt: '2026-10-18T04:00:00Z',
    resolvedAt: '2026-10-20T12:15:00Z',
    closedAt: null,
    holdReasonType: null,
    ...overrides,
  } as GrievanceDetails;
}

function entry(toStatus: HistoryEntry['toStatus'], overrides: Partial<HistoryEntry> = {}): HistoryEntry {
  return {
    fromStatus: null,
    toStatus,
    occurredAt: '2026-10-18T04:00:00Z',
    actorType: 'STAFF',
    actorName: null,
    reason: null,
    holdReasonType: null,
    ...overrides,
  };
}

/** Oldest first, as the backend sends it. */
const HISTORY: HistoryEntry[] = [
  entry('SUBMITTED', { actorType: 'CITIZEN' }),
  entry('ON_HOLD', { occurredAt: '2026-10-19T06:30:00Z', holdReasonType: 'AWAITING_CONTRACTOR' }),
  entry('IN_PROGRESS', { occurredAt: '2026-10-19T10:00:00Z' }),
  entry('RESOLVED', { occurredAt: '2026-10-20T12:15:00Z', reason: 'Bins emptied and area cleaned' }),
];


/** Visible text with element boundaries as spaces: textContent runs "Resolved" and "GRV-…" together. */
function words(node: Element): string {
  const parts: string[] = [];
  const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    parts.push(walker.currentNode.textContent!);
  }
  return parts.join(' ').replace(/\s+/g, ' ').trim();
}

describe('CitizenGrievance', () => {
  let fixture: ComponentFixture<CitizenGrievance>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CitizenGrievance],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CitizenGrievance);
    fixture.componentRef.setInput('id', 'g1');
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function respond(g: GrievanceDetails, history: HistoryEntry[]): Promise<void> {
    backend.expectOne('/api/v1/grievances/g1').flush(g);
    backend.expectOne('/api/v1/grievances/g1/history').flush(history);
    await settle();
  }

  function text(selector: string): string {
    return words(el.querySelector(selector)!);
  }

  function timeline(): string[] {
    return [...el.querySelectorAll('.timeline li')].map(words);
  }

  it('shows status and dates in IST', async () => {
    await respond(details(), HISTORY);

    expect(text('h1')).toBe('Garbage not collected');
    expect(text('.ticket')).toBe('GRV-2026-000042');
    expect(text('.facts')).toBe(
      'Status Resolved Filed 18 Oct 2026, 9:30 AM IST Resolved 20 Oct 2026, 5:45 PM IST',
    );
  });

  it('shows the history newest first, with neutral actors and the hold category', async () => {
    await respond(details(), HISTORY);

    expect(timeline()).toEqual([
      'Resolved 20 Oct 2026, 5:45 PM IST · Municipal staff Bins emptied and area cleaned',
      'In progress 19 Oct 2026, 3:30 PM IST · Municipal staff',
      'On hold · Awaiting contractor 19 Oct 2026, 12:00 PM IST · Municipal staff',
      'Submitted 18 Oct 2026, 9:30 AM IST · You',
    ]);
  });

  it('shows the resolution note while resolved', async () => {
    await respond(details(), HISTORY);
    expect(text('.resolution')).toBe('How it was resolved Bins emptied and area cleaned');
  });

  it('drops the resolution note once the resolution is overturned', async () => {
    const reopened = [...HISTORY, entry('REOPENED', { actorType: 'CITIZEN', reason: 'Still piled up' })];
    await respond(details({ status: 'REOPENED', resolvedAt: null }), reopened);

    expect(el.querySelector('.resolution')).toBeNull();
    expect(timeline()[0]).toContain('Reopened');
    expect(timeline()[0]).toContain('You Still piled up');
  });

  it('shows the current hold category in the status', async () => {
    await respond(
      details({ status: 'ON_HOLD', resolvedAt: null, holdReasonType: 'AWAITING_CONTRACTOR' }),
      HISTORY.slice(0, 2),
    );
    expect(text('.status')).toBe('On hold · Awaiting contractor');
  });

  it('says it could not be found on 404, the same for someone else’s', async () => {
    backend.expectOne('/api/v1/grievances/g1').flush(null, { status: 404, statusText: 'Not Found' });
    backend.expectOne('/api/v1/grievances/g1/history').flush(null, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text('[role="alert"]')).toContain('This grievance could not be found.');
  });
});
