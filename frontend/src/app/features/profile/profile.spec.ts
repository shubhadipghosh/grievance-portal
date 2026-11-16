import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Profile } from './profile';

describe('Profile', () => {
  let fixture: ComponentFixture<Profile>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  const staff = {
    type: 'STAFF',
    fullName: 'Ravi Kumar',
    email: 'ravi@city.gov.in',
    phone: null,
    preferredLocale: 'en',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Profile],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Profile);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function loaded(profile: object = staff): Promise<void> {
    backend.expectOne('/api/v1/profile').flush(profile);
    await settle();
  }

  function input(id: string): HTMLInputElement & HTMLSelectElement {
    return el.querySelector(`#${id}`)!;
  }

  function change(id: string, value: string): void {
    input(id).value = value;
    input(id).dispatchEvent(new Event('input'));
    input(id).dispatchEvent(new Event('change'));
  }

  it('shows the sign-in identity read-only and fills the form', async () => {
    await loaded();
    expect(el.querySelector('.account')!.textContent).toContain('ravi@city.gov.in');
    expect(el.querySelector('.account input')).toBeNull();
    expect(input('fullName').value).toBe('Ravi Kumar');
    expect(input('preferredLocale').value).toBe('en');
  });

  it('shows a citizen their phone, with an empty name to fill in', async () => {
    await loaded({ ...staff, type: 'CITIZEN', fullName: null, email: null, phone: '+919876543210' });
    expect(el.querySelector('.account')!.textContent).toContain('+919876543210');
    expect(input('fullName').value).toBe('');
  });

  it('saves the trimmed name and chosen language', async () => {
    await loaded();
    change('fullName', '  Ravi Das ');
    change('preferredLocale', 'bn');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();

    const req = backend.expectOne('/api/v1/profile');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ fullName: 'Ravi Das', preferredLocale: 'bn' });
    req.flush({ ...staff, fullName: 'Ravi Das', preferredLocale: 'bn' });
    await settle();

    expect(el.querySelector('.saved')!.textContent).toContain('saved');
    expect(input('fullName').value).toBe('Ravi Das');
  });

  it('rejects a blank name without calling the API', async () => {
    await loaded();
    change('fullName', '   ');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
    expect(el.querySelector('#fullName-error')!.textContent).toContain('Enter your name.');
    backend.expectNone('/api/v1/profile');
  });

  it('says when saving fails', async () => {
    await loaded();
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
    backend.expectOne('/api/v1/profile').flush(null, { status: 500, statusText: 'Error' });
    await settle();
    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Could not save');
  });

  it('offers a retry when the profile cannot be loaded', async () => {
    backend.expectOne('/api/v1/profile').flush(null, { status: 500, statusText: 'Error' });
    await settle();
    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await loaded();
    expect(input('fullName').value).toBe('Ravi Kumar');
  });
});
