import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CategorySummary, GrievanceDetails, WardSummary } from '../../../core/api/api.types';
import { FileGrievance } from './file-grievance';

const CATEGORIES: CategorySummary[] = [
  { id: 'garbage', parentId: null, name: 'Garbage' },
  { id: 'pothole', parentId: 'roads', name: 'Pothole' },
  { id: 'roads', parentId: null, name: 'Roads' },
  { id: 'streetlight', parentId: null, name: 'Streetlight' },
];

const WARDS: WardSummary[] = [
  { id: 'w7', name: 'Ward 7' },
  { id: 'w12', name: 'Ward 12' },
];

function details(ticketNo: string): GrievanceDetails {
  return { id: 'g1', ticketNo } as GrievanceDetails;
}

describe('FileGrievance', () => {
  let fixture: ComponentFixture<FileGrievance>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [FileGrievance],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FileGrievance);
    el = fixture.nativeElement;
    // Not whenStable(): the list requests are pending until a test answers them.
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  // Promise.all takes extra microtask turns that whenStable does not wait for.
  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function loadLists(): Promise<void> {
    backend.expectOne('/api/v1/categories').flush(CATEGORIES);
    backend.expectOne('/api/v1/wards').flush(WARDS);
    await settle();
  }

  function set(id: string, value: string): void {
    const control = el.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    control.value = value;
    control.dispatchEvent(new Event('input'));
    control.dispatchEvent(new Event('change'));
  }

  async function fillAndSubmit(): Promise<void> {
    set('category', 'pothole');
    set('ward', 'w12');
    set('title', '  Deep pothole  ');
    set('description', 'Outside the school gate.');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
  }

  it('offers only leaf categories, grouping sub-categories under their parent', async () => {
    await loadLists();
    const select = el.querySelector<HTMLSelectElement>('#category')!;
    const values = [...select.options].map((o) => o.value);
    expect(values).toEqual(['', 'garbage', 'streetlight', 'pothole']);
    const group = select.querySelector('optgroup')!;
    expect(group.label).toBe('Roads');
    expect([...group.querySelectorAll('option')].map((o) => o.value)).toEqual(['pothole']);
  });

  it('lists every ward', async () => {
    await loadLists();
    const values = [...el.querySelectorAll<HTMLOptionElement>('#ward option')].map((o) => o.value);
    expect(values).toEqual(['', 'w7', 'w12']);
  });

  it('shows a retry when the lists fail to load', async () => {
    backend.expectOne('/api/v1/categories').flush(null, { status: 0, statusText: '' });
    backend.expectOne('/api/v1/wards').flush(WARDS);
    await settle();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Could not load the form');

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await loadLists();
    expect(el.querySelector('form')).not.toBeNull();
  });

  it('validates without calling the API', async () => {
    await loadLists();
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
    expect(el.querySelector('#category-error')!.textContent).toContain('Choose a category.');
    expect(el.querySelector('#ward-error')!.textContent).toContain('Choose your ward.');
    expect(el.querySelector('#title-error')!.textContent).toContain('Enter a short title.');
    expect(el.querySelector('#description-error')!.textContent).toContain('Describe the problem.');

    set('title', '   ');
    await settle();
    expect(el.querySelector('#title-error')!.textContent).toContain('Enter a short title.');
    backend.expectNone('/api/v1/grievances');
  });

  it('counts description characters against the 5000 limit', async () => {
    await loadLists();
    const textarea = el.querySelector<HTMLTextAreaElement>('#description')!;
    expect(textarea.getAttribute('maxlength')).toBe('5000');
    expect(el.querySelector('#description-count')!.textContent).toContain('0 / 5000');

    set('description', 'x'.repeat(4321));
    await settle();
    expect(el.querySelector('#description-count')!.textContent).toContain('4321 / 5000');
  });

  it('files the grievance and shows the ticket number prominently', async () => {
    await loadLists();
    await fillAndSubmit();

    const req = backend.expectOne({ method: 'POST', url: '/api/v1/grievances' });
    expect(req.request.body).toEqual({
      categoryId: 'pothole',
      wardId: 'w12',
      title: 'Deep pothole',
      description: 'Outside the school gate.',
      addressText: null,
      latitude: null,
      longitude: null,
    });
    req.flush(details('GRV-2026-000042'), { status: 201, statusText: 'Created' });
    await settle();

    expect(el.querySelector('form')).toBeNull();
    expect(el.querySelector('.ticket')!.textContent!.trim()).toBe('GRV-2026-000042');
    expect(document.activeElement).toBe(el.querySelector('h1'));
  });

  it('sends the address when given', async () => {
    await loadLists();
    set('address', ' Near the temple ');
    await fillAndSubmit();
    const req = backend.expectOne('/api/v1/grievances');
    expect(req.request.body.addressText).toBe('Near the temple');
    req.flush(details('GRV-2026-000043'));
  });

  it('keeps what the citizen typed when filing fails', async () => {
    await loadLists();
    await fillAndSubmit();
    backend
      .expectOne('/api/v1/grievances')
      .flush(null, { status: 422, statusText: 'Unprocessable Content' });
    await settle();

    expect(el.querySelector('[role="alert"]')!.textContent).toContain(
      'can no longer be chosen',
    );
    expect(el.querySelector<HTMLInputElement>('#title')!.value).toBe('  Deep pothole  ');
    expect(el.querySelector('.ticket')).toBeNull();
  });

  it('says so when there is no connection', async () => {
    await loadLists();
    await fillAndSubmit();
    backend.expectOne('/api/v1/grievances').flush(null, { status: 0, statusText: '' });
    await settle();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('No connection');
  });
});
