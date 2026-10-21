import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { appConfig } from '../../app.config';

// Runs against the real appConfig, so removing the XSRF or credentials setup fails here.
describe('HttpClient configuration', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    document.cookie = 'XSRF-TOKEN=token-from-spring; path=/';
  });

  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('copies the XSRF-TOKEN cookie into X-XSRF-TOKEN on a POST', () => {
    firstValueFrom(http.post('/api/v1/auth/staff/login', {}));
    const req = backend.expectOne('/api/v1/auth/staff/login');
    expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('token-from-spring');
  });

  it('sends credentials on every request', () => {
    firstValueFrom(http.get('/api/v1/anything'));
    firstValueFrom(http.post('/api/v1/anything', {}));
    for (const req of backend.match('/api/v1/anything')) {
      expect(req.request.withCredentials).toBe(true);
    }
  });
});
