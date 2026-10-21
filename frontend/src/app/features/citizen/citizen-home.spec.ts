import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { CitizenGrievanceSummary, PagedModel } from '../../core/api/api.types';
import { CitizenHome } from './citizen-home';

function row(id: string, overrides: Partial<CitizenGrievanceSummary> = {}): CitizenGrievanceSummary {
  return {
    id,
    ticketNo: `GRV-2026-00000${id.slice(1)}`,
    status: 'SUBMITTED',
    title: `Grievance ${id}`,
    wardName: 'Ward 12',
    submittedAt: '2026-10-21T04:00:00Z',
    resolvedAt: null,
    ...overrides,
  };
}

function paged(content: CitizenGrievanceSummary[], number = 0, totalPages = 1): PagedModel<CitizenGrievanceSummary> {
  return { content, page: { size: 20, number, totalElements: content.length, totalPages } };
}


/** Visible text with element boundaries as spaces: textContent runs "Resolved" and "GRV-…" together. */
function words(node: Element): string {
  const parts: string[] = [];
  const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    parts.push(walker.currentNode.textContent!);
  }
  return parts.join(' ').replace(/\s+/g, ' ').trim();
}

describe('CitizenHome', () => {
  let fixture: ComponentFixture<CitizenHome>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CitizenHome],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CitizenHome);
    el = fixture.nativeElement;
    // Not whenStable(): the first page is pending until a test answers it.
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

  async function respond(page: number, body: PagedModel<CitizenGrievanceSummary>): Promise<void> {
    backend
      .expectOne((r) => r.url === '/api/v1/grievances/mine' && r.params.get('page') === String(page))
      .flush(body);
    await settle();
  }

  function cards(): string[] {
    return [...el.querySelectorAll('.grievances li')].map(words);
  }

  function lookup(ticketNo: string): void {
    const input = el.querySelector<HTMLInputElement>('#ticketNo')!;
    input.value = ticketNo;
    input.dispatchEvent(new Event('input'));
    el.querySelector<HTMLFormElement>('form.lookup')!.dispatchEvent(new Event('submit'));
  }

  it('lists grievances in the order given, with filing time in IST', async () => {
    await respond(0, paged([row('g2', { status: 'RESOLVED' }), row('g1')]));

    expect(cards()).toEqual([
      'Grievance g2 Resolved GRV-2026-000002 · Ward 12 Filed 21 Oct 2026, 9:30 AM IST',
      'Grievance g1 Submitted GRV-2026-000001 · Ward 12 Filed 21 Oct 2026, 9:30 AM IST',
    ]);
    const link = el.querySelector<HTMLAnchorElement>('.grievances a')!;
    expect(link.getAttribute('href')).toBe('/citizen/grievances/g2');
  });

  it('says so when there are none', async () => {
    await respond(0, paged([]));
    expect(el.textContent).toContain('You have not filed any grievances yet.');
  });

  it('appends the next page on "Show more" and stops at the last', async () => {
    await respond(0, paged([row('g3'), row('g2')], 0, 2));
    el.querySelector<HTMLButtonElement>('.more')!.click();
    await respond(1, paged([row('g1')], 1, 2));

    expect(cards().length).toBe(3);
    expect(el.querySelector('.more')).toBeNull();
  });

  it('offers a retry when the list fails', async () => {
    backend.expectOne('/api/v1/grievances/mine?page=0').flush(null, { status: 0, statusText: '' });
    await settle();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Could not load your grievances');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await respond(0, paged([row('g1')]));
    expect(cards().length).toBe(1);
  });

  it('looks up a ticket typed in lower case and opens it', async () => {
    await respond(0, paged([]));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    lookup(' grv-2026-000042 ');
    await settle();
    backend
      .expectOne((r) => r.url === '/api/v1/grievances' && r.params.get('ticketNo') === 'GRV-2026-000042')
      .flush({ id: 'g42', ticketNo: 'GRV-2026-000042' });
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/citizen/grievances', 'g42']);
  });

  it('checks the ticket format before calling the API', async () => {
    await respond(0, paged([]));
    lookup('12345');
    await settle();

    expect(el.querySelector('#ticketNo-error')!.textContent).toContain('GRV-2026-000123');
    backend.expectNone((r) => r.params.has('ticketNo'));
  });

  it('gives one message for an unknown ticket and someone else’s', async () => {
    await respond(0, paged([]));
    lookup('GRV-2026-000099');
    await settle();
    backend
      .expectOne((r) => r.params.get('ticketNo') === 'GRV-2026-000099')
      .flush(null, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(el.querySelector('.lookup [role="alert"]')!.textContent).toContain(
      'None of your grievances has that ticket number',
    );
  });
});
