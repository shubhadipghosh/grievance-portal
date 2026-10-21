import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { DepartmentSummary, GrievanceSummary, PagedModel } from '../../core/api/api.types';
import { Queue } from './queue';

const NOW = Date.parse('2026-10-21T06:00:00Z');

function summary(overrides: Partial<GrievanceSummary>): GrievanceSummary {
  return {
    id: 'g1',
    ticketNo: 'GRV-2026-000001',
    status: 'SUBMITTED',
    priority: 'NORMAL',
    departmentId: 'd1',
    wardId: 'w1',
    wardName: 'Ward 12',
    title: 'Streetlight out',
    currentAssigneeId: null,
    assigneeName: null,
    slaDueAt: '2026-10-22T00:00:00Z',
    resolvedAt: null,
    ...overrides,
  };
}

function paged(content: GrievanceSummary[], number = 0, totalPages = 1): PagedModel<GrievanceSummary> {
  return { content, page: { size: 20, number, totalElements: content.length, totalPages } };
}

describe('Queue', () => {
  let fixture: ComponentFixture<Queue>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    vi.spyOn(Date, 'now').mockReturnValue(NOW);
    TestBed.configureTestingModule({
      imports: [Queue],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Queue);
    el = fixture.nativeElement;
    // Not whenStable(): the departments request is pending until a test answers it.
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function select(id: string, value: string): void {
    const select = el.querySelector<HTMLSelectElement>(`#${id}`)!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  async function departmentsRespond(departments: DepartmentSummary[]): Promise<void> {
    backend.expectOne('/api/v1/departments').flush(departments);
    await settle();
  }

  function withDepartments(): Promise<void> {
    return departmentsRespond([
      { id: 'd1', name: 'Roads' },
      { id: 'd2', name: 'Water' },
    ]);
  }

  function expectQueue(departmentId: string, status: string, page: number): TestRequest {
    return backend.expectOne(
      (r) =>
        r.url === '/api/v1/grievances' &&
        r.params.get('departmentId') === departmentId &&
        r.params.get('status') === status &&
        r.params.get('page') === String(page),
    );
  }

  async function respond(req: TestRequest, body: PagedModel<GrievanceSummary>): Promise<void> {
    req.flush(body);
    await settle();
  }

  it('loads departments into the dropdown and waits for a choice', async () => {
    expect(el.querySelector('[role="status"]')!.textContent).toContain('Loading departments');
    await withDepartments();
    const options = [...el.querySelectorAll('#department option')].map((o) => o.textContent!.trim());
    expect(options).toEqual(['Select a department', 'Roads', 'Water']);
    expect(el.textContent).toContain('Select a department to see its queue.');
    backend.expectNone('/api/v1/grievances');
  });

  it('selects and loads the only department without asking', async () => {
    await departmentsRespond([{ id: 'd1', name: 'Roads' }]);
    expect(el.querySelector<HTMLSelectElement>('#department')!.value).toBe('d1');
    await respond(expectQueue('d1', 'SUBMITTED', 0), paged([summary({})]));
    expect(el.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('says so when the caller has no departments', async () => {
    await departmentsRespond([]);
    expect(el.textContent).toContain('You are not assigned to any department.');
    backend.expectNone('/api/v1/grievances');
  });

  it('shows an error when departments fail to load, and retries', async () => {
    backend.expectOne('/api/v1/departments').flush(null, { status: 500, statusText: 'Error' });
    await settle();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Could not load departments.');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await withDepartments();
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelectorAll('#department option').length).toBe(3);
  });

  it('shows loading, then the rows, for the selected department and status', async () => {
    await withDepartments();
    select('department', 'd1');
    await settle();
    expect(el.querySelector('[role="status"]')!.textContent).toContain('Loading');

    await respond(
      expectQueue('d1', 'SUBMITTED', 0),
      paged([summary({ currentAssigneeId: 'u9', assigneeName: 'Ravi Sen' })]),
    );
    const cells = [...el.querySelectorAll('tbody td')].map((td) => td.textContent!.trim());
    expect(cells).toEqual([
      'GRV-2026-000001',
      'Streetlight out',
      'Submitted',
      'Ward 12',
      '22 Oct 2026, 05:30',
      'Ravi Sen',
    ]);
  });

  it('shows Unassigned when there is no assignee', async () => {
    await withDepartments();
    select('department', 'd1');
    await respond(expectQueue('d1', 'SUBMITTED', 0), paged([summary({})]));
    expect(el.querySelector('tbody td:last-child')!.textContent!.trim()).toBe('Unassigned');
  });

  it('requests again from page 0 when the status changes', async () => {
    await withDepartments();
    select('department', 'd1');
    await respond(expectQueue('d1', 'SUBMITTED', 0), paged([summary({})]));
    select('status', 'ON_HOLD');
    await respond(expectQueue('d1', 'ON_HOLD', 0), paged([summary({ status: 'ON_HOLD' })]));
    expect(el.querySelector('tbody')!.textContent).toContain('On hold');
  });

  it('shows due dates in IST and marks only running-clock grievances overdue', async () => {
    await withDepartments();
    select('department', 'd1');
    await respond(
      expectQueue('d1', 'SUBMITTED', 0),
      paged([
        summary({ id: 'late', status: 'IN_PROGRESS', slaDueAt: '2026-10-21T05:00:00Z' }),
        summary({ id: 'held', status: 'ON_HOLD', slaDueAt: '2026-10-21T05:00:00Z' }),
        summary({ id: 'fine', status: 'IN_PROGRESS', slaDueAt: '2026-10-21T07:00:00Z' }),
      ]),
    );
    const due = [...el.querySelectorAll('tbody tr')].map((tr) => tr.children[4]);
    expect(due[0].textContent).toContain('21 Oct 2026, 10:30');
    expect(due[0].textContent).toContain('Overdue');
    expect(due[0].classList).toContain('overdue');
    expect(due[1].textContent).not.toContain('Overdue');
    expect(due[2].textContent).not.toContain('Overdue');
  });

  it('marks a grievance resolved after its deadline as resolved late, not overdue', async () => {
    await withDepartments();
    select('status', 'RESOLVED');
    select('department', 'd1');
    await respond(
      expectQueue('d1', 'RESOLVED', 0),
      paged([
        summary({
          id: 'late',
          status: 'RESOLVED',
          slaDueAt: '2026-10-20T00:00:00Z',
          resolvedAt: '2026-10-20T08:00:00Z',
        }),
        summary({
          id: 'onTime',
          status: 'RESOLVED',
          slaDueAt: '2026-10-20T00:00:00Z',
          resolvedAt: '2026-10-19T08:00:00Z',
        }),
      ]),
    );
    const due = [...el.querySelectorAll('tbody tr')].map((tr) => tr.children[4]);
    expect(due[0].querySelector('.resolved-late')!.textContent).toBe('Resolved late');
    expect(due[0].textContent).not.toContain('Overdue');
    expect(due[0].classList).not.toContain('overdue');
    expect(due[1].textContent).not.toContain('Resolved late');
  });

  it('never marks an unresolved grievance resolved late', async () => {
    await withDepartments();
    select('department', 'd1');
    await respond(
      expectQueue('d1', 'SUBMITTED', 0),
      paged([summary({ status: 'IN_PROGRESS', slaDueAt: '2026-10-21T05:00:00Z' })]),
    );
    expect(el.querySelector('.resolved-late')).toBeNull();
    expect(el.querySelector('tbody')!.textContent).toContain('Overdue');
  });

  it('shows an empty state', async () => {
    await withDepartments();
    select('department', 'd1');
    await respond(expectQueue('d1', 'SUBMITTED', 0), paged([], 0, 0));
    expect(el.textContent).toContain('No grievances in this department with status “Submitted”.');
    expect(el.querySelector('table')).toBeNull();
  });

  it('shows an error state and retries', async () => {
    await withDepartments();
    select('department', 'd1');
    expectQueue('d1', 'SUBMITTED', 0).flush(null, { status: 500, statusText: 'Error' });
    await settle();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Could not load the queue.');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await respond(expectQueue('d1', 'SUBMITTED', 0), paged([summary({})]));
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('pages using the PagedModel metadata', async () => {
    await withDepartments();
    select('department', 'd1');
    await respond(expectQueue('d1', 'SUBMITTED', 0), paged([summary({})], 0, 3));
    const [prev, next] = el.querySelectorAll<HTMLButtonElement>('.pager button');
    expect(el.querySelector('.pager')!.textContent).toContain('Page 1 of 3');
    expect(prev.disabled).toBe(true);
    expect(next.disabled).toBe(false);

    next.click();
    await respond(expectQueue('d1', 'SUBMITTED', 1), paged([summary({})], 2, 3));
    const [, lastNext] = el.querySelectorAll<HTMLButtonElement>('.pager button');
    expect(el.querySelector('.pager')!.textContent).toContain('Page 3 of 3');
    expect(lastNext.disabled).toBe(true);
  });

  it('ignores a slow response that a later request has superseded', async () => {
    await withDepartments();
    select('department', 'd1');
    const slow = expectQueue('d1', 'SUBMITTED', 0);
    select('department', 'd2');
    await respond(expectQueue('d2', 'SUBMITTED', 0), paged([summary({ title: 'Water main burst' })]));
    await respond(slow, paged([summary({ title: 'Streetlight out' })]));
    expect(el.querySelector('tbody')!.textContent).toContain('Water main burst');
    expect(el.querySelector('tbody')!.textContent).not.toContain('Streetlight out');
  });

  it('navigates to the grievance when a row is clicked', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await withDepartments();
    select('department', 'd1');
    await respond(expectQueue('d1', 'SUBMITTED', 0), paged([summary({ id: 'g42' })]));
    el.querySelector<HTMLElement>('tbody tr td:nth-child(2)')!.click();
    expect(navigate).toHaveBeenCalledWith(['/grievances', 'g42']);
  });
});
