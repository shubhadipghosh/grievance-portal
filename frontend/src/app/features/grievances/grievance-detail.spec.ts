import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  ActivityItem,
  CurrentUser,
  GrievanceDetails,
  TicketOverview,
  UserType,
} from '../../core/api/api.types';
import { AuthService } from '../../core/auth/auth.service';
import { GrievanceDetail } from './grievance-detail';

function overview(
  overrides: Partial<TicketOverview> = {},
  grievance: Partial<GrievanceDetails> = {},
): TicketOverview {
  return {
    grievance: {
      id: 'g1',
      ticketNo: 'GRV-2026-000042',
      status: 'ASSIGNED',
      priority: 'NORMAL',
      title: 'Garbage not collected',
      description: 'Bins overflowing for three days.',
      addressText: 'Near the market gate',
      latitude: null,
      longitude: null,
      submittedAt: '2026-10-18T04:00:00Z',
      slaDueAt: '2026-10-20T04:00:00Z',
      resolvedAt: null,
      closedAt: null,
      holdReasonType: null,
      ...grievance,
    } as GrievanceDetails,
    categoryName: 'Garbage',
    departmentName: 'Sanitation',
    wardName: 'Ward 12',
    reporterName: 'Asha Das',
    assigneeName: 'Ravi Sen',
    departmentAdmins: ['Sanitation Admin'],
    canComment: true,
    canCommentInternal: false,
    actions: [],
    ...overrides,
  };
}

function item(kind: ActivityItem['kind'], overrides: Partial<ActivityItem> = {}): ActivityItem {
  return {
    kind,
    at: '2026-10-18T04:00:00Z',
    actorType: 'STAFF',
    actorName: 'Sanitation Admin',
    body: null,
    internal: null,
    fromStatus: null,
    toStatus: null,
    holdReasonType: null,
    reason: null,
    assigneeName: null,
    previousAssigneeName: null,
    fromDepartment: null,
    toDepartment: null,
    fromCategory: null,
    toCategory: null,
    ...overrides,
  };
}

/** Oldest first, as the backend sends it. */
const ACTIVITY: ActivityItem[] = [
  item('STATUS', { actorType: 'CITIZEN', actorName: 'Asha Das', toStatus: 'SUBMITTED' }),
  item('STATUS', { at: '2026-10-18T05:00:00Z', fromStatus: 'ACKNOWLEDGED', toStatus: 'ASSIGNED' }),
  item('ASSIGNMENT', {
    at: '2026-10-18T05:00:00Z',
    assigneeName: 'Mita Paul',
    previousAssigneeName: 'Ravi Sen',
  }),
  item('REROUTE', {
    at: '2026-10-18T06:00:00Z',
    fromDepartment: 'Water Supply',
    toDepartment: 'Sanitation',
  }),
  item('COMMENT', {
    at: '2026-10-18T07:00:00Z',
    actorType: 'CITIZEN',
    actorName: 'Asha Das',
    body: 'Still overflowing',
    internal: false,
  }),
];

/** Visible text with element boundaries as spaces. */
function words(node: Element): string {
  const parts: string[] = [];
  const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    parts.push(walker.currentNode.textContent!);
  }
  return parts.join(' ').replace(/\s+/g, ' ').trim();
}

describe('GrievanceDetail', () => {
  let fixture: ComponentFixture<GrievanceDetail>;
  let backend: HttpTestingController;
  let el: HTMLElement;
  const user = signal<CurrentUser | null>(null);

  function create(type: UserType): void {
    user.set({ id: 'u1', type });
    TestBed.configureTestingModule({
      imports: [GrievanceDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { currentUser: user.asReadonly() } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GrievanceDetail);
    fixture.componentRef.setInput('id', 'g1');
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

  async function respond(o: TicketOverview, activity: ActivityItem[] = ACTIVITY): Promise<void> {
    backend.expectOne('/api/v1/grievances/g1/overview').flush(o);
    backend.expectOne('/api/v1/grievances/g1/activity').flush(activity);
    await settle();
  }

  function text(selector: string): string {
    return words(el.querySelector(selector)!);
  }

  function feed(): string[] {
    return [...el.querySelectorAll('.feed > li')].map(words);
  }

  function detail(term: string): string {
    const row = [...el.querySelectorAll('.details dl > div')].find(
      (d) => words(d.querySelector('dt')!) === term,
    );
    return words(row!.querySelector('dd')!);
  }

  function button(label: string): HTMLButtonElement {
    return [...el.querySelectorAll('button')].find(
      (b) => words(b) === label || words(b).startsWith(label + ' ('),
    )!;
  }

  async function type(value: string): Promise<void> {
    const box = el.querySelector<HTMLTextAreaElement>('#comment-body')!;
    box.value = value;
    box.dispatchEvent(new Event('input'));
    await settle();
  }

  describe('as staff', () => {
    beforeEach(() => create('STAFF'));

    it('shows the ticket header and description', async () => {
      await respond(overview());

      expect(text('.ticket-meta')).toBe('GRV-2026-000042 Assigned');
      expect(text('h1')).toBe('Garbage not collected');
      expect(text('.description')).toBe('Bins overflowing for three days.');
      expect(text('.location')).toBe('Location Near the market gate');
      expect(text('a.back')).toBe('← Queue');
    });

    it('shows actions only when there are some, and reloads after one', async () => {
      await respond(overview());
      expect(el.querySelector('app-ticket-actions')).toBeNull();

      fixture.componentInstance['load']();
      await respond(overview({ actions: [{ to: 'IN_PROGRESS', reasonRequired: false }] }));
      expect(text('app-ticket-actions .buttons')).toBe('Start work');

      button('Start work').click();
      await settle();
      el.querySelector<HTMLButtonElement>('app-ticket-actions form button[type="submit"]')!.click();
      await settle();
      backend.expectOne('/api/v1/grievances/g1/in-progress').flush({});
      await settle();

      await respond(overview({ actions: [] }, { status: 'IN_PROGRESS' }));
      expect(text('.ticket-meta')).toBe('GRV-2026-000042 In progress');
      expect(el.querySelector('app-ticket-actions')).toBeNull();
    });

    it('fills the details panel', async () => {
      await respond(overview());

      expect(detail('Assignee')).toBe('RS Ravi Sen');
      expect(detail('Department')).toBe('Sanitation');
      expect(detail('Department admin')).toBe('SA Sanitation Admin');
      expect(detail('Ward')).toBe('Ward 12');
      expect(detail('Category')).toBe('Garbage');
      expect(detail('Raised by')).toBe('Asha Das');
      expect(detail('Raised on')).toBe('18 Oct 2026, 9:30 AM IST');
      expect(detail('Proposed resolution by')).toMatch(
        /^20 Oct 2026, 9:30 AM IST Overdue by \d+ days$/,
      );
    });

    it('says when nobody is assigned', async () => {
      await respond(overview({ assigneeName: null, departmentAdmins: [] }));

      expect(detail('Assignee')).toBe('Unassigned');
      expect(detail('Department admin')).toBe('None assigned');
    });

    it('shows comments and events newest first', async () => {
      await respond(overview());

      expect(feed()).toEqual([
        'AD Asha Das Citizen 18 Oct 2026, 12:30 PM IST Still overflowing',
        'Sanitation Admin moved this from Water Supply to Sanitation 18 Oct 2026, 11:30 AM IST',
        'Sanitation Admin changed the assignee from Ravi Sen to Mita Paul 18 Oct 2026, 10:30 AM IST',
        'Sanitation Admin changed status Acknowledged → Assigned 18 Oct 2026, 10:30 AM IST',
        'Asha Das raised this grievance 18 Oct 2026, 9:30 AM IST',
      ]);
    });

    it('filters to comments only, and to history only', async () => {
      await respond(overview());

      button('Comments').click();
      fixture.detectChanges();
      expect(feed()).toHaveLength(1);
      expect(text('.feed')).toContain('Still overflowing');

      button('History').click();
      fixture.detectChanges();
      expect(feed()).toHaveLength(4);
      expect(text('.feed')).not.toContain('Still overflowing');
    });

    it('marks an internal remark', async () => {
      await respond(overview(), [
        item('COMMENT', { body: 'Check contractor rates', internal: true, actorName: 'Ravi Sen' }),
      ]);

      expect(el.querySelector('.comment.internal')).not.toBeNull();
      expect(text('.comment')).toContain('Internal');
    });

    it('posts an internal remark and adds it to the feed', async () => {
      await respond(overview({ canCommentInternal: true }));

      button('Internal remark').click();
      fixture.detectChanges();
      expect(el.querySelector('.composer.internal')).not.toBeNull();
      await type('  Check contractor rates  ');
      button('Add remark').click();

      const req = backend.expectOne('/api/v1/grievances/g1/comments');
      expect(req.request.body).toEqual({ body: 'Check contractor rates', internal: true });
      req.flush(
        item('COMMENT', {
          at: '2026-10-19T04:00:00Z',
          body: 'Check contractor rates',
          internal: true,
          actorName: 'Ravi Sen',
        }),
      );
      await settle();

      expect(feed()[0]).toContain('Check contractor rates');
      expect(el.querySelector<HTMLTextAreaElement>('#comment-body')!.value).toBe('');
      expect(el.querySelector('.composer.internal')).toBeNull();
    });

    it('keeps the draft when posting fails', async () => {
      await respond(overview());
      await type('Crew sent');
      button('Comment').click();

      backend
        .expectOne('/api/v1/grievances/g1/comments')
        .flush(null, { status: 0, statusText: 'Unknown Error' });
      await settle();

      expect(text('.composer [role="alert"]')).toContain('no connection');
      expect(el.querySelector<HTMLTextAreaElement>('#comment-body')!.value).toBe('Crew sent');
    });

    it('hides the comment box from a viewer who may not comment', async () => {
      await respond(overview({ canComment: false }));
      expect(el.querySelector('.composer')).toBeNull();
    });
  });

  describe('as a citizen', () => {
    beforeEach(() => create('CITIZEN'));

    it('calls the citizen "You" and offers no internal remark', async () => {
      await respond(overview());

      expect(feed().at(-1)).toBe('You raised this grievance 18 Oct 2026, 9:30 AM IST');
      expect(detail('Raised by')).toBe('You');
      expect(el.querySelector('.modes')).toBeNull();
      expect(text('a.back')).toBe('← My grievances');
    });

    it('cannot post a blank comment', async () => {
      await respond(overview());
      await type('   ');
      expect(button('Comment').disabled).toBe(true);
    });

    it('shows the resolution note while resolved', async () => {
      await respond(overview({}, { status: 'RESOLVED', resolvedAt: '2026-10-20T04:00:00Z' }), [
        item('STATUS', { fromStatus: 'IN_PROGRESS', toStatus: 'RESOLVED', reason: 'Bins emptied' }),
      ]);

      expect(text('.resolution')).toBe('How it was resolved Bins emptied');
      expect(detail('Proposed resolution by')).toBe('20 Oct 2026, 9:30 AM IST');
    });

    it('says it could not be found on 404', async () => {
      backend
        .expectOne('/api/v1/grievances/g1/overview')
        .flush(null, { status: 404, statusText: 'Not Found' });
      backend
        .expectOne('/api/v1/grievances/g1/activity')
        .flush(null, { status: 404, statusText: 'Not Found' });
      await settle();

      expect(text('[role="alert"]')).toContain('This grievance could not be found.');
    });
  });
});
