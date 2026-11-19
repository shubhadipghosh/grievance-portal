import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { StaffLogin } from './staff-login';

describe('StaffLogin', () => {
  let fixture: ComponentFixture<StaffLogin>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [StaffLogin],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StaffLogin);
    el = fixture.nativeElement;
    await fixture.whenStable();
  });

  function type(id: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submitValid(): Promise<void> {
    type('email', 'officer@example.gov');
    type('password', 'secret');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    // login() waits for the session check before posting.
    backend.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await Promise.resolve();
    await Promise.resolve();
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('validates required fields and email format without calling the API', async () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
    expect(el.querySelector('#email-error')!.textContent).toContain('Enter your email address.');
    expect(el.querySelector('#password-error')!.textContent).toContain('Enter your password.');

    type('email', 'not-an-email');
    await settle();
    expect(el.querySelector('#email-error')!.textContent).toContain('Enter a valid email address.');
    backend.expectNone('/api/v1/auth/staff/login');
  });

  it('posts credentials and navigates to /queue', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await submitValid();
    const req = backend.expectOne('/api/v1/auth/staff/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'officer@example.gov', password: 'secret' });
    req.flush({ id: 'u1', type: 'STAFF' });
    await settle();
    expect(navigate).toHaveBeenCalledWith('/queue');
  });

  it('sends a user with a generated password to choose their own', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await submitValid();
    backend
      .expectOne('/api/v1/auth/staff/login')
      .flush({ id: 'u1', type: 'STAFF', mustChangePassword: true });
    await settle();
    expect(navigate).toHaveBeenCalledWith('/change-password');
  });

  it.each([
    [401, {}, 'Incorrect email or password.'],
    [429, { 'Retry-After': '45' }, 'Too many attempts. Try again in 45 seconds.'],
    [429, { 'Retry-After': '900' }, 'Too many attempts. Try again in 15 minutes.'],
    [500, {}, 'Something went wrong. Please try again.'],
  ])('shows the right message for %i %o', async (status, headers, message) => {
    await submitValid();
    backend
      .expectOne('/api/v1/auth/staff/login')
      .flush(null, { status, statusText: 'Error', headers });
    await settle();
    expect(el.querySelector('[role="alert"]')!.textContent!.trim()).toBe(message);
  });
});
