import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { CanActivateFn, provideRouter, Router, UrlTree } from '@angular/router';
import { authGuard, citizenGuard, staffGuard } from './auth.guard';

describe('auth guards', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  /** Runs the guard with /auth/me answering as the given user, or 401 for null. */
  async function run(guard: CanActivateFn, user: object | null): Promise<string | true> {
    const result = TestBed.runInInjectionContext(
      () => guard({} as never, {} as never) as Promise<boolean | UrlTree>,
    );
    const me = backend.expectOne('/api/v1/auth/me');
    if (user) {
      me.flush(user);
    } else {
      me.flush(null, { status: 401, statusText: 'Unauthorized' });
    }
    const outcome = await result;
    return outcome === true || TestBed.inject(Router).serializeUrl(outcome as UrlTree);
  }

  const staff = { id: 'u1', type: 'STAFF' };
  const citizen = { id: 'u2', type: 'CITIZEN' };

  it('authGuard: redirects without a session', async () => {
    expect(await run(authGuard, null)).toBe('/login');
  });

  it('authGuard: allows any signed-in user', async () => {
    expect(await run(authGuard, citizen)).toBe(true);
  });

  it('staffGuard: redirects without a session', async () => {
    expect(await run(staffGuard, null)).toBe('/login');
  });

  it('staffGuard: redirects a citizen session', async () => {
    expect(await run(staffGuard, citizen)).toBe('/login');
  });

  it('staffGuard: allows staff', async () => {
    expect(await run(staffGuard, staff)).toBe(true);
  });

  it('citizenGuard: redirects without a session to the OTP login', async () => {
    expect(await run(citizenGuard, null)).toBe('/citizen/login');
  });

  it('citizenGuard: redirects a staff session', async () => {
    expect(await run(citizenGuard, staff)).toBe('/citizen/login');
  });

  it('citizenGuard: allows a citizen', async () => {
    expect(await run(citizenGuard, citizen)).toBe(true);
  });
});
