import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { UserMenu } from './user-menu';

describe('UserMenu', () => {
  let fixture: ComponentFixture<UserMenu>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [UserMenu],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UserMenu);
    el = fixture.nativeElement;
  });

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Signs in as the given user type, answering the profile load with the given profile. */
  async function signIn(type: 'CITIZEN' | 'STAFF', profile: object): Promise<void> {
    TestBed.inject(AuthService).restoreSession();
    backend.expectOne('/api/v1/auth/me').flush({ id: 'u1', type });
    await settle();
    backend.expectOne('/api/v1/profile').flush({ type, preferredLocale: 'en', ...profile });
    await settle();
  }

  function toggle(): HTMLButtonElement {
    return el.querySelector<HTMLButtonElement>('button[aria-label="Account"]')!;
  }

  it('shows nothing while signed out', async () => {
    TestBed.inject(AuthService).restoreSession();
    backend.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await settle();
    expect(toggle()).toBeNull();
    backend.expectNone('/api/v1/profile');
  });

  it('shows initials, and the name and contact when opened', async () => {
    await signIn('STAFF', { fullName: 'Ravi Kumar Das', email: 'ravi@city.gov.in', phone: null });
    expect(toggle().textContent!.trim()).toBe('RD');
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(el.querySelector('#account-panel')).toBeNull();

    toggle().click();
    await settle();
    expect(toggle().getAttribute('aria-expanded')).toBe('true');
    const panel = el.querySelector('#account-panel')!;
    expect(panel.textContent).toContain('Ravi Kumar Das');
    expect(panel.textContent).toContain('ravi@city.gov.in');
    expect(panel.querySelector('a')!.getAttribute('href')).toBe('/profile');
  });

  it('falls back to an icon and "Citizen" for a citizen without a name', async () => {
    await signIn('CITIZEN', { fullName: null, email: null, phone: '+919876543210' });
    expect(toggle().querySelector('svg')).not.toBeNull();
    toggle().click();
    await settle();
    expect(el.querySelector('#account-panel')!.textContent).toContain('Citizen');
    expect(el.querySelector('#account-panel')!.textContent).toContain('+919876543210');
  });

  it('closes on Escape and returns focus to the button', async () => {
    await signIn('STAFF', { fullName: 'Ravi', email: 'ravi@city.gov.in', phone: null });
    toggle().click();
    await settle();
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await settle();
    expect(el.querySelector('#account-panel')).toBeNull();
    expect(document.activeElement).toBe(toggle());
  });

  it.each([
    ['CITIZEN', '/citizen/login'],
    ['STAFF', '/login'],
  ] as const)('signs a %s out and goes to %s', async (type, loginUrl) => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await signIn(type, { fullName: 'Ravi', email: null, phone: null });
    toggle().click();
    await settle();

    el.querySelector<HTMLButtonElement>('.sign-out')!.click();
    backend.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    backend.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await settle();

    expect(navigate).toHaveBeenCalledWith(loginUrl);
    expect(toggle()).toBeNull();
  });

  it('stays signed in and says so when sign-out fails', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await signIn('STAFF', { fullName: 'Ravi', email: null, phone: null });
    toggle().click();
    await settle();

    el.querySelector<HTMLButtonElement>('.sign-out')!.click();
    backend.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    await settle();

    expect(el.querySelector('[role="alert"]')!.textContent).toContain('Could not sign out');
    expect(navigate).not.toHaveBeenCalled();
  });
});
