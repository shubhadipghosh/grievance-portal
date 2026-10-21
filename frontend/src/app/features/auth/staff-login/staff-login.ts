import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { email, form, FormField, required, submit } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-staff-login',
  imports: [FormField],
  templateUrl: './staff-login.html',
  styleUrl: './staff-login.scss',
})
export class StaffLogin {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loginForm = form(signal({ email: '', password: '' }), (p) => {
    required(p.email, { message: 'Enter your email address.' });
    email(p.email, { message: 'Enter a valid email address.' });
    required(p.password, { message: 'Enter your password.' });
  });
  protected readonly error = signal<string | null>(null);

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    await submit(this.loginForm, async (f) => {
      try {
        await this.auth.login(f().value());
        await this.router.navigateByUrl('/queue');
      } catch (e) {
        this.error.set(errorMessage(e));
      }
      return undefined;
    });
  }
}

function errorMessage(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 401) {
      return 'Incorrect email or password.';
    }
    if (e.status === 429) {
      const seconds = Number(e.headers.get('Retry-After'));
      return seconds > 0
        ? `Too many attempts. Try again in ${formatWait(seconds)}.`
        : 'Too many attempts. Try again later.';
    }
  }
  return 'Something went wrong. Please try again.';
}

function formatWait(seconds: number): string {
  if (seconds < 60) {
    return seconds === 1 ? '1 second' : `${seconds} seconds`;
  }
  const minutes = Math.ceil(seconds / 60);
  return minutes === 1 ? '1 minute' : `${minutes} minutes`;
}
