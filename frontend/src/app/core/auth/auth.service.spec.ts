import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('AuthService', () => {
  let auth: AuthService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
  });

  // Spring's logout expires the XSRF-TOKEN cookie. A login POST sent before
  // another API response re-issues it carries no X-XSRF-TOKEN and gets a 403.
  it('does not send a login after logout until a fresh XSRF token has been fetched', async () => {
    auth.restoreSession();
    backend.expectOne('/api/v1/auth/me').flush({ id: 'u1', type: 'STAFF' });
    await tick();

    const loggedOut = auth.logout();
    backend.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await tick();
    const refresh = backend.expectOne('/api/v1/auth/me');

    const login = auth.login({ email: 'officer@example.gov', password: 'secret' });
    await tick();
    backend.expectNone('/api/v1/auth/staff/login');

    refresh.flush(null, { status: 401, statusText: 'Unauthorized' });
    await loggedOut;
    await tick();
    backend.expectOne('/api/v1/auth/staff/login').flush({ id: 'u1', type: 'STAFF' });
    expect(await login).toEqual({ id: 'u1', type: 'STAFF' });
  });

  it('clears the current user on logout', async () => {
    auth.restoreSession();
    backend.expectOne('/api/v1/auth/me').flush({ id: 'u1', type: 'STAFF' });
    await tick();
    expect(auth.isAuthenticated()).toBe(true);

    const loggedOut = auth.logout();
    backend.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await tick();
    expect(auth.isAuthenticated()).toBe(false);
    backend.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await loggedOut;
  });
});
