import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { CitizenLogin } from './citizen-login';

describe('CitizenLogin', () => {
  let fixture: ComponentFixture<CitizenLogin>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    // Real setTimeout, so whenStable still works; fake only the countdown's clock.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    TestBed.configureTestingModule({
      imports: [CitizenLogin],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CitizenLogin);
    el = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function type(id: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function flushPending(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
  }

  /** Submits the phone step; the first request waits for the session check. */
  async function submitPhone(digits = '9876543210'): Promise<void> {
    type('phone', digits);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    backend.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await flushPending();
  }

  async function reachCodeStep(): Promise<void> {
    await submitPhone();
    backend.expectOne('/api/v1/auth/otp/send').flush(null, { status: 204, statusText: 'No Content' });
    await settle();
  }

  function alertText(): string {
    return el.querySelector('[role="alert"]')!.textContent!.trim();
  }

  function resendButton(): HTMLButtonElement {
    return el.querySelector<HTMLButtonElement>('#resend')!;
  }

  it('validates the phone without calling the API', async () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
    expect(el.querySelector('#phone-error')!.textContent).toContain('Enter your mobile number.');

    for (const bad of ['98765', '5876543210', '+919876543210', '98765432101']) {
      type('phone', bad);
      await settle();
      expect(el.querySelector('#phone-error')!.textContent).toContain(
        'Enter a valid 10-digit mobile number.',
      );
    }
    backend.expectNone('/api/v1/auth/otp/send');
  });

  it('sends the 10 digits as +91 E.164 and moves to the code step', async () => {
    await submitPhone('9876543210');
    const req = backend.expectOne('/api/v1/auth/otp/send');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ phone: '+919876543210' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    await settle();

    const code = el.querySelector<HTMLInputElement>('#code')!;
    expect(code.getAttribute('inputmode')).toBe('numeric');
    expect(code.getAttribute('autocomplete')).toBe('one-time-code');
    expect(code.getAttribute('maxlength')).toBe('6');
    expect(el.querySelector('label[for="code"]')!.textContent).toContain('+919876543210');
    expect(document.activeElement).toBe(code);
  });

  it.each([
    [400, {}, 'Enter a valid 10-digit mobile number.'],
    [429, { 'Retry-After': '45' }, 'Too many attempts. Try again in 45 seconds.'],
    [429, { 'Retry-After': '3600' }, 'Too many attempts. Try again in 60 minutes.'],
    [0, {}, 'No connection. Check your mobile data and try again.'],
    [500, {}, 'Something went wrong. Please try again.'],
  ])('send failure %i %o shows the right message and stays on the phone step', async (status, headers, message) => {
    await submitPhone();
    backend.expectOne('/api/v1/auth/otp/send').flush(null, { status, statusText: 'Error', headers });
    await settle();
    expect(alertText()).toBe(message);
    expect(el.querySelector('#phone')).not.toBeNull();
    expect(el.querySelector('#code')).toBeNull();
  });

  it('keeps resend disabled for 60 seconds, then resends', async () => {
    await reachCodeStep();
    expect(resendButton().disabled).toBe(true);
    expect(resendButton().textContent).toContain('Resend code in 60s');

    vi.advanceTimersByTime(59_000);
    await settle();
    expect(resendButton().disabled).toBe(true);
    expect(resendButton().textContent).toContain('Resend code in 1s');

    vi.advanceTimersByTime(1_000);
    await settle();
    expect(resendButton().disabled).toBe(false);

    resendButton().click();
    await flushPending();
    const req = backend.expectOne('/api/v1/auth/otp/send');
    expect(req.request.body).toEqual({ phone: '+919876543210' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(el.querySelector('[role="status"]')!.textContent).toContain('A new code has been sent.');
    expect(resendButton().disabled).toBe(true);
    expect(resendButton().textContent).toContain('Resend code in 60s');
  });

  it('shows retryAfter when a resend is rate limited', async () => {
    await reachCodeStep();
    vi.advanceTimersByTime(60_000);
    await settle();

    resendButton().click();
    await flushPending();
    backend
      .expectOne('/api/v1/auth/otp/send')
      .flush(null, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '120' } });
    await settle();
    expect(alertText()).toBe('Too many attempts. Try again in 2 minutes.');
  });

  it('validates the code without calling the API', async () => {
    await reachCodeStep();
    type('code', '12a45');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle();
    expect(el.querySelector('#code-error')!.textContent).toContain('The code is 6 digits.');
    backend.expectNone('/api/v1/auth/otp/verify');
  });

  it('verifies phone and code and navigates to /citizen', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await reachCodeStep();
    type('code', '123456');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await flushPending();
    const req = backend.expectOne('/api/v1/auth/otp/verify');
    expect(req.request.body).toEqual({ phone: '+919876543210', code: '123456' });
    req.flush({ id: 'c1', type: 'CITIZEN' });
    await settle();
    expect(navigate).toHaveBeenCalledWith('/citizen');
  });

  it('shows one generic message for a failed verify', async () => {
    await reachCodeStep();
    type('code', '000000');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await flushPending();
    backend
      .expectOne('/api/v1/auth/otp/verify')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await settle();
    expect(alertText()).toBe(
      'That code did not work. Check it and try again, or request a new code.',
    );
  });

  it('returns to the phone step with the number kept', async () => {
    await reachCodeStep();
    el.querySelector<HTMLButtonElement>('.secondary button:last-child')!.click();
    await settle();
    expect(el.querySelector<HTMLInputElement>('#phone')!.value).toBe('9876543210');
  });
});
