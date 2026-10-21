import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { email, form, FormField, required, submit } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { tooManyAttemptsMessage } from '../../../core/auth/rate-limit';

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
      return tooManyAttemptsMessage(e);
    }
  }
  return 'Something went wrong. Please try again.';
}
