import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CurrentUser,
  OtpSendRequest,
  OtpVerifyRequest,
  StaffLoginRequest,
} from '../api/api.types';

@Service()
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly user = signal<CurrentUser | null>(null);
  private restored?: Promise<void>;

  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.user() !== null);

  /**
   * Restores an existing session via /auth/me. Runs once; later calls return
   * the same promise, so the guard can wait for it after a page reload.
   */
  restoreSession(): Promise<void> {
    this.restored ??= this.loadSession();
    return this.restored;
  }

  async login(credentials: StaffLoginRequest): Promise<CurrentUser> {
    // The first API response sets the XSRF-TOKEN cookie. Wait for it, or a
    // login submitted before /auth/me returns is rejected for missing CSRF.
    await this.restoreSession();
    const user = await firstValueFrom(
      this.http.post<CurrentUser>('/api/v1/auth/staff/login', credentials),
    );
    this.user.set(user);
    return user;
  }

  /** Texts a login code. Rejects with 400 for an invalid phone, 429 when rate limited. */
  async sendOtp(request: OtpSendRequest): Promise<void> {
    // Same CSRF wait as login().
    await this.restoreSession();
    await firstValueFrom(this.http.post<void>('/api/v1/auth/otp/send', request));
  }

  /** Rejects with 401 for any failure; the backend never says why. */
  async verifyOtp(request: OtpVerifyRequest): Promise<CurrentUser> {
    await this.restoreSession();
    const user = await firstValueFrom(
      this.http.post<CurrentUser>('/api/v1/auth/otp/verify', request),
    );
    this.user.set(user);
    return user;
  }

  async logout(): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/v1/auth/logout', null));
    this.user.set(null);
    // Logout also expires the XSRF-TOKEN cookie. Re-checking the session gets
    // a fresh one, and login() waits for it, so the next login is not a 403.
    this.restored = this.loadSession();
    await this.restored;
  }

  private loadSession(): Promise<void> {
    return this.me().then(
      (user) => this.user.set(user),
      () => this.user.set(null),
    );
  }

  /** The session's user, or null when there is no session (401). */
  async me(): Promise<CurrentUser | null> {
    try {
      return await firstValueFrom(this.http.get<CurrentUser>('/api/v1/auth/me'));
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 401) {
        return null;
      }
      throw e;
    }
  }
}
