import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { CurrentUser } from '../../../core/api/api.types';
import { AuthService } from '../../../core/auth/auth.service';
import { ChangePassword } from './change-password';

describe('ChangePassword', () => {
  let fixture: ComponentFixture<ChangePassword>;
  let backend: HttpTestingController;
  let auth: AuthService;
  let el: HTMLElement;

  /** Signs in as the user (as the guard would have), then opens the screen. */
  async function create(user: CurrentUser): Promise<void> {
    TestBed.configureTestingModule({
      imports: [ChangePassword],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    const restored = auth.restoreSession();
    backend.expectOne('/api/v1/auth/me').flush(user);
    await restored;

    fixture = TestBed.createComponent(ChangePassword);
    el = fixture.nativeElement;
    await settle();
  }

  afterEach(() => backend.verify());

  async function settle(): Promise<void> {
    for (let i = 0; i < 3; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function type(id: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function error(id: string): string {
    return el.querySelector(`#${id}-error`)!.textContent!.trim();
  }

  async function fillAndSubmit(current: string, next: string, confirm = next): Promise<void> {
    type('current-password', current);
    type('new-password', next);
    type('confirm-password', confirm);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
  }

  const forcedStaff: CurrentUser = { id: 'u1', type: 'STAFF', mustChangePassword: true };
  const staff: CurrentUser = { id: 'u1', type: 'STAFF', mustChangePassword: false };

  it('explains why when the change is forced, with no way back', async () => {
    await create(forcedStaff);
    expect(el.querySelector('h1')!.textContent).toBe('Choose your password');
    expect(el.querySelector('label[for="current-password"]')!.textContent).toBe(
      'Password you were given',
    );
    expect(el.querySelector('a.back')).toBeNull();
  });

  it('offers a way back when the change is voluntary', async () => {
    await create(staff);
    expect(el.querySelector('h1')!.textContent).toBe('Change password');
    expect(el.querySelector('a.back')!.getAttribute('href')).toBe('/queue');
  });

  it('checks length and the repeat without calling the API', async () => {
    await create(staff);
    await fillAndSubmit('old one', 'too short');
    expect(error('new-password')).toBe('Use at least 12 characters.');

    await fillAndSubmit('old one', 'a much better passphrase', 'a much better passphrasf');
    expect(error('confirm-password')).toBe('The passwords do not match.');

    backend.expectNone('/api/v1/auth/password');
  });

  it('saves and continues to the queue when forced, clearing the flag', async () => {
    await create(forcedStaff);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fillAndSubmit('Gq7mTz4XkP2nRb8w', 'a much better passphrase');

    const req = backend.expectOne('/api/v1/auth/password');
    expect(req.request.body).toEqual({
      currentPassword: 'Gq7mTz4XkP2nRb8w',
      newPassword: 'a much better passphrase',
    });
    req.flush(null, { status: 204, statusText: 'No Content' });
    await settle();

    expect(auth.currentUser()!.mustChangePassword).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/queue');
  });

  it('confirms in place and clears the form when voluntary', async () => {
    await create(staff);
    await fillAndSubmit('old password here', 'a much better passphrase');
    backend.expectOne('/api/v1/auth/password').flush(null, { status: 204, statusText: 'No Content' });
    await settle();

    expect(el.querySelector('[role="status"]')!.textContent).toContain('has been changed');
    expect(el.querySelector<HTMLInputElement>('#new-password')!.value).toBe('');
  });

  it.each([
    [422, {}, { detail: 'The current password is incorrect.' }, 'The current password is incorrect.'],
    [429, { 'Retry-After': '600' }, null, 'Too many attempts. Try again in 10 minutes.'],
    [500, {}, null, 'Something went wrong. Please try again.'],
  ])('shows the right message for %i', async (status, headers, body, message) => {
    await create(staff);
    await fillAndSubmit('old password here', 'a much better passphrase');
    backend
      .expectOne('/api/v1/auth/password')
      .flush(body, { status, statusText: 'Error', headers });
    await settle();

    expect(el.querySelector('[role="alert"]')!.textContent!.trim()).toBe(message);
    expect(auth.currentUser()!.mustChangePassword).toBe(false);
  });
});
