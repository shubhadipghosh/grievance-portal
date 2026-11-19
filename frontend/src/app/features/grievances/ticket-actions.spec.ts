import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GrievanceStatus, TicketAction } from '../../core/api/api.types';
import { TicketActions } from './ticket-actions';

describe('TicketActions', () => {
  let fixture: ComponentFixture<TicketActions>;
  let backend: HttpTestingController;
  let el: HTMLElement;
  let changed: number;

  function create(status: GrievanceStatus, actions: TicketAction[], isCitizen = false): void {
    TestBed.configureTestingModule({
      imports: [TicketActions],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketActions);
    fixture.componentRef.setInput('grievanceId', 'g1');
    fixture.componentRef.setInput('status', status);
    fixture.componentRef.setInput('actions', actions);
    fixture.componentRef.setInput('isCitizen', isCitizen);
    changed = 0;
    fixture.componentInstance.changed.subscribe(() => changed++);
    el = fixture.nativeElement;
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function labels(): string[] {
    return [...el.querySelectorAll('.buttons button')].map((b) => b.textContent!.trim());
  }

  function button(label: string, scope = el): HTMLButtonElement {
    return [...scope.querySelectorAll('button')].find((b) => b.textContent!.trim() === label)!;
  }

  function submitButton(): HTMLButtonElement {
    return el.querySelector<HTMLButtonElement>('form button[type="submit"]')!;
  }

  async function open(label: string): Promise<void> {
    button(label, el.querySelector('.buttons')!).click();
    await settle();
  }

  async function fill(selector: string, value: string): Promise<void> {
    const control = el.querySelector<HTMLInputElement>(selector)!;
    control.value = value;
    control.dispatchEvent(new Event('input'));
    control.dispatchEvent(new Event('change'));
    await settle();
  }

  async function submit(): Promise<void> {
    submitButton().click();
    await settle();
  }

  it('words each IN_PROGRESS transition by where it starts', () => {
    const actions: TicketAction[] = [{ to: 'IN_PROGRESS', reasonRequired: false }];
    create('ASSIGNED', actions);
    expect(labels()).toEqual(['Start work']);

    fixture.componentRef.setInput('status', 'ON_HOLD');
    fixture.detectChanges();
    expect(labels()).toEqual(['Resume work']);

    fixture.componentRef.setInput('status', 'RESOLVED');
    fixture.detectChanges();
    expect(labels()).toEqual(['Undo resolution']);
  });

  it('offers a citizen to confirm or reopen', () => {
    create(
      'RESOLVED',
      [
        { to: 'CLOSED', reasonRequired: false },
        { to: 'REOPENED', reasonRequired: true },
      ],
      true,
    );
    expect(labels()).toEqual(['Confirm and close', 'Reopen']);
  });

  it('acknowledges without a note', async () => {
    create('SUBMITTED', [{ to: 'ACKNOWLEDGED', reasonRequired: false }]);
    await open('Acknowledge');
    expect(submitButton().disabled).toBe(false);

    await submit();
    const req = backend.expectOne('/api/v1/grievances/g1/acknowledge');
    expect(req.request.body).toEqual({ reason: null });
    req.flush({});
    await settle();

    expect(changed).toBe(1);
    expect(el.querySelector('form')).toBeNull();
  });

  it('will not reject without a reason', async () => {
    create('SUBMITTED', [{ to: 'REJECTED', reasonRequired: true }]);
    await open('Reject');
    expect(submitButton().disabled).toBe(true);

    await fill('#action-reason', '  duplicate of GRV-2026-000041 ');
    expect(submitButton().disabled).toBe(false);
    await submit();
    expect(backend.expectOne('/api/v1/grievances/g1/reject').request.body).toEqual({
      reason: 'duplicate of GRV-2026-000041',
    });
  });

  it('assigns to someone from the eligible list', async () => {
    create('ACKNOWLEDGED', [{ to: 'ASSIGNED', reasonRequired: false }]);
    await open('Assign');
    backend.expectOne('/api/v1/grievances/g1/assignees').flush([
      { id: 's1', fullName: 'Mita Paul' },
      { id: 's2', fullName: 'Ravi Sen' },
    ]);
    await settle();
    expect(submitButton().disabled).toBe(true);

    await fill('#action-assignee', 's2');
    await submit();
    expect(backend.expectOne('/api/v1/grievances/g1/assign').request.body).toEqual({
      assigneeId: 's2',
      reason: null,
    });
  });

  it('holds for the citizen without naming anyone', async () => {
    create('IN_PROGRESS', [{ to: 'ON_HOLD', reasonRequired: true }]);
    await open('Put on hold');
    await fill('#action-hold-reason', 'AWAITING_CITIZEN');
    expect(el.querySelector('#action-hold-owner')).toBeNull();
    await fill('#action-reason', 'need the exact location');

    await submit();
    expect(backend.expectOne('/api/v1/grievances/g1/hold').request.body).toEqual({
      reasonType: 'AWAITING_CITIZEN',
      ownerType: 'CITIZEN',
      holdOwnerId: null,
      holdOwnerNote: null,
      reason: 'need the exact location',
    });
  });

  it('holds for an outside party only once it is named', async () => {
    create('IN_PROGRESS', [{ to: 'ON_HOLD', reasonRequired: true }]);
    await open('Put on hold');
    await fill('#action-hold-reason', 'AWAITING_CONTRACTOR');
    await fill('#action-reason', 'waiting on parts');
    expect(submitButton().disabled).toBe(true);

    await fill('#action-hold-owner', 'Ganguly Contractors');
    await submit();
    expect(backend.expectOne('/api/v1/grievances/g1/hold').request.body).toEqual({
      reasonType: 'AWAITING_CONTRACTOR',
      ownerType: 'EXTERNAL',
      holdOwnerId: null,
      holdOwnerNote: 'Ganguly Contractors',
      reason: 'waiting on parts',
    });
  });

  it('reloads the page when someone else changed the grievance first', async () => {
    create('SUBMITTED', [{ to: 'ACKNOWLEDGED', reasonRequired: false }]);
    await open('Acknowledge');
    await submit();
    backend
      .expectOne('/api/v1/grievances/g1/acknowledge')
      .flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
    await settle();

    expect(changed).toBe(1);
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('changed while you were');
  });

  it('keeps the form open and says why when not allowed', async () => {
    create('SUBMITTED', [{ to: 'REJECTED', reasonRequired: true }]);
    await open('Reject');
    await fill('#action-reason', 'duplicate');
    await submit();
    backend
      .expectOne('/api/v1/grievances/g1/reject')
      .flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });
    await settle();

    expect(changed).toBe(0);
    expect(el.querySelector('form')).not.toBeNull();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('not allowed');
  });
});
